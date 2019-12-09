package decaf.frontend.typecheck;

import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.driver.error.*;
import decaf.frontend.scope.FormalScope;
import decaf.frontend.scope.LocalScope;
import decaf.frontend.scope.Scope;
import decaf.frontend.scope.ScopeStack;
import decaf.frontend.symbol.*;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.*;
import decaf.lowlevel.log.IndentPrinter;
import decaf.printing.PrettyScope;

import javax.lang.model.type.ErrorType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import static decaf.frontend.type.BuiltInType.ERROR;
import static decaf.frontend.type.BuiltInType.VOID;

/**
 * The typer phase: type check abstract syntax tree and annotate nodes with inferred (and checked) types.
 */
public class Typer extends Phase<Tree.TopLevel, Tree.TopLevel> implements TypeLitVisited {

    public Typer(Config config) {
        super("typer", config);
    }

    @Override
    public Tree.TopLevel transform(Tree.TopLevel tree) {
        var ctx = new ScopeStack(tree.globalScope);
        tree.accept(this, ctx);
        return tree;
    }

    @Override
    public void onSucceed(Tree.TopLevel tree) {
        if (config.target.equals(Config.Target.PA2)) {
            var printer = new PrettyScope(new IndentPrinter(config.output));
            printer.pretty(tree.globalScope);
            printer.flush();
        }
    }

    @Override
    public void visitTopLevel(Tree.TopLevel program, ScopeStack ctx) {
        ctx.lambdaClass = program.lambdaClass;
        for (var clazz : program.classes) {
            clazz.accept(this, ctx);
        }
    }

    @Override
    public void visitClassDef(Tree.ClassDef clazz, ScopeStack ctx) {
        ctx.open(clazz.symbol.scope);
        for (var field : clazz.fields) {
            field.accept(this, ctx);
        }
        ctx.close();
    }

    @Override
    public void visitMethodDef(Tree.MethodDef method, ScopeStack ctx) {
        loopLevel.push(0);
        ctx.open(method.symbol.scope);
        if (method.body.isPresent()) {
            method.body.get().accept(this, ctx);
            if (!method.symbol.type.returnType.isVoidType() && !method.body.get().returns) {
                issue(new MissingReturnError(method.body.get().pos));
            }
        }
        ctx.close();
        loopLevel.pop();
    }

    /**
     * To determine if a break statement is legal or not, we need to know if we are inside a loop, i.e.
     * loopLevel {@literal >} 1?
     * <p>
     * Increase this counter when entering a loop, and decrease it when leaving a loop.
     */
    private Stack<Integer> loopLevel = new Stack<>();

    @Override
    public void visitBlock(Tree.Block block, ScopeStack ctx) {
        ctx.open(block.scope);
        for (var stmt : block.stmts) {
            stmt.accept(this, ctx);
        }
        ctx.close();
        block.returns = !block.stmts.isEmpty() && block.stmts.get(block.stmts.size() - 1).returns;

        for (var stmt : block.stmts) {
            block.returnStmt.addAll(stmt.returnStmt);
            block.returnType.addAll(stmt.returnType);
        }
    }

    @Override
    public void visitAssign(Tree.Assign stmt, ScopeStack ctx) {
        stmt.lhs.accept(this, ctx);
        stmt.rhs.accept(this, ctx);

        var lt = stmt.lhs.type;
        var rt = stmt.rhs.type;

        if (!lt.noError()) {
            return;
        }

        if (stmt.lhs instanceof Tree.VarSel) {
            var lhs = ((Tree.VarSel) stmt.lhs);
            if (lhs.receiver.isEmpty() && lhs.symbol.isVarSymbol()) {
                var symbol = (VarSymbol) lhs.symbol;
                if (symbol.isLocalVar() || symbol.isParam()) {
                    if (ctx.currentLambda().isPresent()) {
                        if (ctx.lookupBeforeForLambda(lhs.symbol.name, ctx.currentLambda().get().pos).isPresent()) {
                            issue(new BadCaptureError(stmt.pos));
                        }
                    }
                }
            }
        }
        if(stmt.lhs instanceof Tree.VarSel) {
            if (((Tree.VarSel) stmt.lhs).symbol.isMethodSymbol()) {
                issue(new AssignMemberMethodError(stmt.pos, stmt.lhs.name));
            }
        }
        if (!rt.subtypeOf(lt)) {
            issue(new IncompatBinOpError(stmt.pos, lt.toString(), "=", rt.toString()));
        }
    }

    @Override
    public void visitExprEval(Tree.ExprEval stmt, ScopeStack ctx) {
        stmt.expr.accept(this, ctx);
    }


    @Override
    public void visitIf(Tree.If stmt, ScopeStack ctx) {
        checkTestExpr(stmt.cond, ctx);
        stmt.trueBranch.accept(this, ctx);
        stmt.falseBranch.ifPresent(b -> b.accept(this, ctx));
        // if-stmt returns a value iff both branches return
        stmt.returns = stmt.trueBranch.returns && stmt.falseBranch.isPresent() && stmt.falseBranch.get().returns;
        stmt.returnStmt.addAll(stmt.trueBranch.returnStmt);
        stmt.returnType.addAll(stmt.trueBranch.returnType);
        if (stmt.falseBranch.isPresent()) {
            stmt.returnStmt.addAll(stmt.falseBranch.get().returnStmt);
            stmt.returnType.addAll(stmt.falseBranch.get().returnType);
        }
    }

    @Override
    public void visitWhile(Tree.While loop, ScopeStack ctx) {
        checkTestExpr(loop.cond, ctx);
        loopLevel.push(loopLevel.peek() + 1);
        loop.body.accept(this, ctx);
        loopLevel.push(loopLevel.peek() - 1);
        loop.returnStmt.addAll(loop.body.returnStmt);
        loop.returnType.addAll(loop.body.returnType);
    }

    @Override
    public void visitFor(Tree.For loop, ScopeStack ctx) {
        ctx.open(loop.scope);
        loop.init.accept(this, ctx);
        checkTestExpr(loop.cond, ctx);
        loop.update.accept(this, ctx);
        loopLevel.push(loopLevel.peek() + 1);
        for (var stmt : loop.body.stmts) {
            stmt.accept(this, ctx);
        }
        loopLevel.push(loopLevel.peek() - 1);
        ctx.close();
        loop.returnStmt.addAll(loop.init.returnStmt);
        loop.returnType.addAll(loop.init.returnType);
        loop.returnStmt.addAll(loop.update.returnStmt);
        loop.returnType.addAll(loop.update.returnType);
        for (var stmt : loop.body.stmts) {
            loop.returnStmt.addAll(stmt.returnStmt);
            loop.returnType.addAll(stmt.returnType);
        }
    }

    @Override
    public void visitBreak(Tree.Break stmt, ScopeStack ctx) {
        if (loopLevel.peek() == 0) {
            issue(new BreakOutOfLoopError(stmt.pos));
        }
    }

    @Override
    public void visitReturn(Tree.Return stmt, ScopeStack ctx) {
        if (ctx.currentMethod().type.isVarType()) {
            stmt.expr.ifPresent(e -> e.accept(this, ctx));
            var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
            stmt.returnStmt.add(stmt);
            stmt.returnType.add(actual);
            stmt.returns = stmt.expr.isPresent();
        } else {
            var expected = ((FunType) ctx.currentMethod().type).returnType;
            stmt.expr.ifPresent(e -> e.accept(this, ctx));
            var actual = stmt.expr.map(e -> e.type).orElse(BuiltInType.VOID);
            if (actual.noError() && !actual.subtypeOf(expected)) {
                issue(new BadReturnTypeError(stmt.pos, expected.toString(), actual.toString()));
            }
            stmt.returns = stmt.expr.isPresent();
        }
    }

    @Override
    public void visitPrint(Tree.Print stmt, ScopeStack ctx) {
        int i = 0;
        for (var expr : stmt.exprs) {
            expr.accept(this, ctx);
            i++;
            if (expr.type.noError() && !expr.type.isBaseType()) {
                issue(new BadPrintArgError(expr.pos, Integer.toString(i), expr.type.toString()));
            }
        }
    }

    private void checkTestExpr(Tree.Expr expr, ScopeStack ctx) {
        expr.accept(this, ctx);
        if (expr.type.noError() && !expr.type.eq(BuiltInType.BOOL)) {
            issue(new BadTestExpr(expr.pos));
        }
    }

    // Expressions

    @Override
    public void visitIntLit(Tree.IntLit that, ScopeStack ctx) {
        that.type = BuiltInType.INT;
    }

    @Override
    public void visitBoolLit(Tree.BoolLit that, ScopeStack ctx) {
        that.type = BuiltInType.BOOL;
    }

    @Override
    public void visitStringLit(Tree.StringLit that, ScopeStack ctx) {
        that.type = BuiltInType.STRING;
    }

    @Override
    public void visitNullLit(Tree.NullLit that, ScopeStack ctx) {
        that.type = BuiltInType.NULL;
    }

    @Override
    public void visitReadInt(Tree.ReadInt readInt, ScopeStack ctx) {
        readInt.type = BuiltInType.INT;
    }

    @Override
    public void visitReadLine(Tree.ReadLine readStringExpr, ScopeStack ctx) {
        readStringExpr.type = BuiltInType.STRING;
    }

    @Override
    public void visitUnary(Tree.Unary expr, ScopeStack ctx) {
        expr.operand.accept(this, ctx);
        var t = expr.operand.type;
        if (t.noError() && !compatible(expr.op, t)) {
            // Only report this error when the operand has no error, to avoid nested errors flushing.
            issue(new IncompatUnOpError(expr.pos, Tree.opStr(expr.op), t.toString()));
        }

        // Even when it doesn't type check, we could make a fair guess based on the operator kind.
        // Let's say the operator is `-`, then one possibly wants an integer as the operand.
        // Once he/she fixes the operand, according to our type inference rule, the whole unary expression
        // must have type int! Thus, we simply _assume_ it has type int, rather than `NoType`.
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.UnaryOp op, Type operand) {
        return switch (op) {
            case NEG -> operand.eq(BuiltInType.INT); // if e : int, then -e : int
            case NOT -> operand.eq(BuiltInType.BOOL); // if e : bool, then !e : bool
        };
    }

    public Type resultTypeOf(Tree.UnaryOp op) {
        return switch (op) {
            case NEG -> BuiltInType.INT;
            case NOT -> BuiltInType.BOOL;
        };
    }

    @Override
    public void visitBinary(Tree.Binary expr, ScopeStack ctx) {
        expr.lhs.accept(this, ctx);
        expr.rhs.accept(this, ctx);
        var t1 = expr.lhs.type;
        var t2 = expr.rhs.type;
        if (t1.noError() && t2.noError() && !compatible(expr.op, t1, t2)) {
            issue(new IncompatBinOpError(expr.pos, t1.toString(), Tree.opStr(expr.op), t2.toString()));
        }
        expr.type = resultTypeOf(expr.op);
    }

    public boolean compatible(Tree.BinaryOp op, Type lhs, Type rhs) {
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            // if e1, e2 : int, then e1 + e2 : int
            return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
        }

        if (op.equals(Tree.BinaryOp.AND) || op.equals(Tree.BinaryOp.OR)) { // logic
            // if e1, e2 : bool, then e1 && e2 : bool
            return lhs.eq(BuiltInType.BOOL) && rhs.eq(BuiltInType.BOOL);
        }

        if (op.equals(Tree.BinaryOp.EQ) || op.equals(Tree.BinaryOp.NE)) { // eq
            // if e1 : T1, e2 : T2, T1 <: T2 or T2 <: T1, then e1 == e2 : bool
            return lhs.subtypeOf(rhs) || rhs.subtypeOf(lhs);
        }

        // compare
        // if e1, e2 : int, then e1 > e2 : bool
        return lhs.eq(BuiltInType.INT) && rhs.eq(BuiltInType.INT);
    }

    public Type resultTypeOf(Tree.BinaryOp op) {
        if (op.compareTo(Tree.BinaryOp.ADD) >= 0 && op.compareTo(Tree.BinaryOp.MOD) <= 0) { // arith
            return BuiltInType.INT;
        }
        return BuiltInType.BOOL;
    }

    @Override
    public void visitNewArray(Tree.NewArray expr, ScopeStack ctx) {
        expr.elemType.accept(this, ctx);
        expr.length.accept(this, ctx);
        var et = expr.elemType.type;
        var lt = expr.length.type;

        if (et.isVoidType()) {
            issue(new BadArrElementError(expr.elemType.pos));
            expr.type = ERROR;
        } else {
            expr.type = new ArrayType(et);
        }

        if (lt.noError() && !lt.eq(BuiltInType.INT)) {
            issue(new BadNewArrayLength(expr.length.pos));
        }
    }

    @Override
    public void visitNewClass(Tree.NewClass expr, ScopeStack ctx) {
        var clazz = ctx.lookupClass(expr.clazz.name);
        if (clazz.isPresent()) {
            if (clazz.get().isAbstract()) {
                issue(new InstantiateAbstractClassError(expr.pos, clazz.get().name));
            }
            expr.symbol = clazz.get();
            expr.type = expr.symbol.type;
        } else {
            issue(new ClassNotFoundError(expr.pos, expr.clazz.name));
            expr.type = ERROR;
        }
    }

    @Override
    public void visitThis(Tree.This expr, ScopeStack ctx) {
        if (ctx.currentMemberMethod().isStatic()) {
            issue(new ThisInStaticFuncError(expr.pos));
        }
        expr.type = ctx.currentClass().type;
        expr.isThis = true;
    }

    private boolean allowClassNameVar = false;

    @Override
    public void visitVarSel(Tree.VarSel expr, ScopeStack ctx) {
        boolean isMethod = false;
        boolean thisClass = false;
        boolean isStaticMethod = false;
        expr.type = ERROR;
//        System.err.printf("visit varsel @ %s\n", expr.pos);

        if (expr.receiver.isEmpty()) {
            var symbol = ctx.lookupBefore(expr.name, expr.pos);
            if (symbol.isPresent()) {
                if (symbol.get().isMethodSymbol())
                    isMethod = true;
                else if (symbol.get().isVarSymbol())
                    isMethod = false;
                else if(symbol.get().isClassSymbol())
                    isMethod = false;
            }
            else
            {
                issue(new UndeclVarError(expr.pos, expr.name));
                return;
            }
        }
        else
        {
            var receiver = expr.receiver.get();
            allowClassNameVar = true;
            receiver.accept(this, ctx);
            allowClassNameVar = false;
            var rt = receiver.type;
            if (!rt.noError())
                return;
            else if (rt.isClassType()) {
                var ct = (ClassType) rt;
                var field = ctx.getClass(ct.name).scope.lookup(expr.name);
                if (field.isPresent()) {
                    if (field.get().isMethodSymbol())
                        isMethod = true;
                    else if (field.get().isVarSymbol())
                        isMethod = false;
                } else {
                    issue(new FieldNotFoundError(expr.pos, expr.name, rt.toString()));
                    return;
                }
            }
            else if(rt.isArrayType())
                isMethod = true;
            else {
                issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
                return;
            }
        }

        if (isMethod)
        {
            Type rt;

            if (expr.receiver.isPresent()) {
                var receiver = expr.receiver.get();
                rt = receiver.type;

                if (receiver instanceof Tree.VarSel) {
                    var v1 = (Tree.VarSel) receiver;
                    if (v1.isClassName) {
                        // Special case: invoking a static method, like MyClass.foo()
                        typeMethod(expr, false, v1.name, ctx, true);
                        // do not have to capture anything
                        return;
                    }
                }
            } else {
                thisClass = true;
                rt = ctx.currentClass().type;
            }


            if (rt.hasError()) {
                return;
            }

            if (rt.isArrayType() && expr.name.equals("length")) { // Special case: array.length()
                expr.type = new FunType(BuiltInType.INT, new ArrayList<>());
                expr.name = "length";
                expr.isArrayLength = true;
//                return;
            } else if (rt.isClassType()) {
                isStaticMethod = typeMethod(expr, thisClass, ((ClassType) rt).name, ctx, false);
                if (thisClass && !isStaticMethod) {
                    expr.setThis();
                    expr.receiver.get().accept(this, ctx);
                }
            } else {
                issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
                return;
            }
        } else {
            if (expr.receiver.isEmpty()) {
                // Variable, which should be complicated since a legal variable could refer to a local var,
                // a visible member var, and a class name.
                var symbol = ctx.lookupBefore(expr.name, expr.pos);
                if (symbol.isPresent()) {
                    if (symbol.get().isVarSymbol()) {
                        var var = (VarSymbol) symbol.get();
                        expr.symbol = var;
                        expr.type = var.type;
                        if (var.isMember()) {
                            if (ctx.currentMemberMethod().isStatic()) {
                                issue(new RefNonStaticError(expr.pos, ctx.currentMemberMethod().name, expr.name));
                            } else {
                                expr.setThis();
                                thisClass = true;
                                expr.receiver.get().accept(this, ctx);
                            }
                        }
                    } else if (symbol.get().isClassSymbol() && allowClassNameVar) { // special case: a class name
                        var clazz = (ClassSymbol) symbol.get();
                        expr.type = clazz.type;
                        expr.isClassName = true;
                        expr.symbol = clazz;
                    } else {
                        issue(new UndeclVarError(expr.pos, expr.name));
                        return;
                    }
                } else {
                    issue(new UndeclVarError(expr.pos, expr.name));
                    return;
                }
            } else {
                // has receiver
                var receiver = expr.receiver.get();
                var rt = receiver.type;

                if (receiver instanceof Tree.VarSel) {
                    var v1 = (Tree.VarSel) receiver;
                    if (v1.isClassName) {
                        // special case like MyClass.foo: report error cannot access field 'foo' from 'class : MyClass'
                        issue(new NotClassFieldError(expr.pos, expr.name, ctx.getClass(v1.name).type.toString()));
                        return;
                    }
                }

                if (!rt.noError()) {
                    return;
                }

                if (!rt.isClassType()) {
                    issue(new NotClassFieldError(expr.pos, expr.name, rt.toString()));
                    return;
                }

                var ct = (ClassType) rt;
                var field = ctx.getClass(ct.name).scope.lookup(expr.name);
                if (field.isPresent() && field.get().isVarSymbol()) {
                    var var = (VarSymbol) field.get();
                    if (var.isMember()) {
                        expr.symbol = var;
                        expr.type = var.type;
                        if (!ctx.currentClass().type.subtypeOf(var.getOwner().type)) {
                            // member vars are protected
                            issue(new FieldNotAccessError(expr.pos, expr.name, ct.toString()));
                        }
                    }
                } else if (field.isEmpty()) {
                    issue(new FieldNotFoundError(expr.pos, expr.name, ct.toString()));
                } else {
                    issue(new NotClassFieldError(expr.pos, expr.name, ct.toString()));
                }
            }
        }
        if (ctx.currentLambda().isPresent()) {
            if (!isStaticMethod) {
                if (thisClass || expr.receiver.isPresent() && expr.receiver.get().isThis) {
                    if (expr.receiver.isEmpty()) {
                        throw(new NullPointerException());
                    }
                    ctx.capture(ctx.currentClass());
                    expr.currLambdaScope = Optional.of(ctx.currentLambda().get().scope);
                    System.err.printf("capture %s in %s\n", expr.receiver.get(), expr.currLambdaScope);
                } else if (expr.receiver.isEmpty()) {
                    if (!expr.isClassName) {
                        ctx.capture(expr.symbol);
                        expr.currLambdaScope = Optional.of(ctx.currentLambda().get().scope);
                        if (!expr.currLambdaScope.get().capturedSymbol.contains(expr.symbol)) {
                            expr.currLambdaScope = Optional.empty();
                        }
                        System.err.printf("capture %s in %s\n", expr.symbol, expr.currLambdaScope);
                    }
                }
            }
        }
//        if (ctx.currentLambda().isPresent()) {
//            if (!isStaticMethod) {
//                if (thisClass) {
//                    ctx.capture(ctx.currentClass());
//                } else if (expr.receiver.isPresent()) {
//                    if (expr.receiver.get().isThis) {
//                        ctx.capture(ctx.currentClass());
//                    } else if (expr.receiver.get() instanceof Tree.VarSel) {
//                        ctx.capture(((Tree.VarSel) expr.receiver.get()).symbol);
//                    } else {
//                        // other receiver : maybe new class, new array and so on
////                    System.err.printf("ERROR : unexpected captured symbol @ %s\n", expr.pos);
////                    System.err.printf("receiver : %s\n", expr.receiver.get());
////                    throw new NullPointerException();
//                    }
//                } else {
//                    ctx.capture(expr.symbol);
//                }
//            }
//        }
    }

    private boolean typeMethod(Tree.VarSel expr, boolean thisClass, String className, ScopeStack ctx, boolean requireStatic) {
        var clazz = thisClass ? ctx.currentClass() : ctx.getClass(className);
        var symbol = clazz.scope.lookup(expr.name);
        boolean isStaticMethod = false;
        if (symbol.isPresent()) {
            if (symbol.get().isMethodSymbol()) {
                var method = (MethodSymbol) symbol.get();


                if (requireStatic && !method.isStatic()) {
                    issue(new NotClassFieldError(expr.pos, expr.name, clazz.type.toString()));
                    return false;
                }

                // Cannot call this's member methods in a static method
                if (thisClass && ctx.currentMemberMethod().isStatic() && !method.isStatic()) {
                    issue(new RefNonStaticError(expr.pos, ctx.currentMemberMethod().name, method.name));
                }

                isStaticMethod = method.isStatic();
                expr.symbol = method;
                expr.type = method.type;
                expr.name = method.name;
            } else {
                issue(new NotClassMethodError(expr.pos, expr.name, clazz.type.toString()));
            }
        } else {
            issue(new FieldNotFoundError(expr.pos, expr.name, clazz.type.toString()));
        }
        return isStaticMethod;
    }

    @Override
    public void visitIndexSel(Tree.IndexSel expr, ScopeStack ctx) {
        expr.array.accept(this, ctx);
        expr.index.accept(this, ctx);
        var at = expr.array.type;
        var it = expr.index.type;

        if (at.hasError()) {
            expr.type = ERROR;
            return;
        }

        if (!at.isArrayType()) {
            issue(new NotArrayError(expr.array.pos));
            expr.type = ERROR;
            return;
        }

        expr.type = ((ArrayType) at).elementType;
        if (!it.eq(BuiltInType.INT)) {
            issue(new SubNotIntError(expr.pos));
        }
    }

    @Override
    public void visitCall(Tree.Call call, ScopeStack ctx) {
        call.type = ERROR;

        call.expr.accept(this, ctx);
        if (!call.expr.type.noError())
        {
            return;
        }
        if (!call.expr.type.isFuncType())
        {
            issue(new NotCallableTypeError(call.expr.type.toString(), call.pos));
            return;
        }

        call.methodName = call.expr.name;

        // typing args
        var args = call.args;
        for (var arg : args) {
            arg.accept(this, ctx);
        }

        // check signature compatibility
        if (((FunType)call.expr.type).arity() != args.size()) {
            issue(new BadArgCountError(call.pos, call.methodName, ((FunType)call.expr.type).arity(), args.size()));
        }
        var iter1 = ((FunType)call.expr.type).argTypes.iterator();
        var iter2 = call.args.iterator();
        for (int i = 1; iter1.hasNext() && iter2.hasNext(); i++) {
            Type t1 = iter1.next();
            Tree.Expr e = iter2.next();
            Type t2 = e.type;
            if (t2.noError() && !t2.subtypeOf(t1)) {
                issue(new BadArgTypeError(e.pos, i, t2.toString(), t1.toString()));
            }
        }
        call.type = ((FunType) call.expr.type).returnType;
    }

    @Override
    public void visitClassTest(Tree.ClassTest expr, ScopeStack ctx) {
        expr.obj.accept(this, ctx);
        expr.type = BuiltInType.BOOL;

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }
        var clazz = ctx.lookupClass(expr.is.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.is.name));
        } else {
            expr.symbol = clazz.get();
        }
    }

    @Override
    public void visitClassCast(Tree.ClassCast expr, ScopeStack ctx) {
        expr.obj.accept(this, ctx);

        if (!expr.obj.type.isClassType()) {
            issue(new NotClassError(expr.obj.type.toString(), expr.pos));
        }

        var clazz = ctx.lookupClass(expr.to.name);
        if (clazz.isEmpty()) {
            issue(new ClassNotFoundError(expr.pos, expr.to.name));
            expr.type = ERROR;
        } else {
            expr.symbol = clazz.get();
            expr.type = expr.symbol.type;
        }
    }

    @Override
    public void visitLocalVarDef(Tree.LocalVarDef stmt, ScopeStack ctx) {
        if (stmt.initVal.isEmpty()) return;

        var initVal = stmt.initVal.get();
        ctx.undeclare(stmt.symbol);
        initVal.accept(this, ctx);
        ctx.declare(stmt.symbol);
        if (stmt.symbol.type.isVarType())
        {
            if (initVal.type.isVoidType())
            {
                issue(new BadVarTypeError(stmt.pos, stmt.name));
                stmt.symbol.type = ERROR;
                return;
            }
            stmt.symbol.type = initVal.type;
            ctx.redeclare(stmt.symbol);
        }

        var lt = stmt.symbol.type;
        var rt = initVal.type;
        if (lt.noError() && !rt.subtypeOf(lt)) {
            issue(new IncompatBinOpError(stmt.assignPos, lt.toString(), "=", rt.toString()));
        }
    }

    @Override
    public void visitLambdaDef(Tree.LambdaDef lambda, ScopeStack ctx) {
        lambda.currMethod = ctx.currentMethod();
        List<Type> argTypes = new ArrayList<>();
        for (var param : lambda.params) {
            argTypes.add(param.typeLit.get().type);
        }
        loopLevel.push(0);
        ctx.open(lambda.symbol.scope);
        for (var param : lambda.params)
            param.accept(this, ctx);
        if (lambda.kind == Tree.LambdaDef.Kind.EXPR) {
            ctx.open(lambda.exprScope);
            lambda.expr.accept(this, ctx);
            lambda.type = new FunType(lambda.expr.type, argTypes);
            ctx.close();
        } else {
            lambda.block.accept(this, ctx);
            Type returnType;
            if (lambda.block.returnType.isEmpty()) {
                returnType = VOID;
            } else {
                returnType = lambda.block.returnType.get(0);
                for (int i = 1; i < lambda.block.returnType.size(); i++) {
                    var currType = lambda.block.returnType.get(i);
                    returnType = getUpperTypeBound(returnType, currType);
                }
            }
            if (!returnType.isVoidType()) {
                if(!lambda.block.returns)
                    issue(new MissingReturnError(lambda.block.pos));
                }
            if (returnType.hasError()) {
                issue(new IncompatReturnTypesError(lambda.block.pos));
                lambda.type = ERROR;
            } else {
                lambda.type = new FunType(returnType, argTypes);
            }
        }
        lambda.symbol.type = lambda.type;


//        System.err.printf("%s\n", ctx.lambdaClass);
        var methodSymbol = new MethodSymbol(lambda.pos.toString(), (FunType)lambda.type, new FormalScope(),
                lambda.pos, new Tree.Modifiers(Tree.Modifiers.STATIC, lambda.pos), ctx.lambdaClass);
        ctx.lambdaClass.scope.declare(methodSymbol);

        ctx.close();
        ctx.redeclare(lambda.symbol);
        loopLevel.pop();
    }

    // Only usage: check if an initializer cyclically refers to the declared variable, e.g. var x = x + 1
//    private Optional<Pos> localVarDefPos = Optional.empty();

    public Type getUpperTypeBound(Type type1, Type type2) {
        if (type1.hasError() || type2.hasError()) {
            return ERROR;
        }
        if (type1.isNullType()) {
            if (!type2.isNullType() && !type2.isClassType()) {
                return ERROR;
            } else {
                return type2;
            }
        } else if (type1.isVoidType() || type1.isBaseType() || type1.isArrayType()) {
            if (!type1.eq(type2)) {
                return ERROR;
            } else {
                return type1;
            }
        } else if (type1.isClassType()) {
            if (!type2.isClassType()) {
                return ERROR;
            }
            while (true) {
                if (type2.subtypeOf(type1)) {
                    return type1;
                } else if (((ClassType) type1).superType.isPresent()) {
                    type1 = ((ClassType) type1).superType.get();
                } else {
                    return ERROR;
                }
            }
        } else if (type1.isFuncType()) {
            if (((FunType) type1).argTypes.size() != ((FunType) type2).argTypes.size()) {
                return ERROR;
            }
            var returnType = getUpperTypeBound(((FunType) type1).returnType, ((FunType) type2).returnType);
            if (returnType.hasError()) {
                return ERROR;
            }
            List<Type> argTypes = new ArrayList<>();
            for (var i = 0; i < ((FunType) type1).argTypes.size(); i++) {
                var type = getLowerTypeBound(((FunType) type1).argTypes.get(i), ((FunType) type2).argTypes.get(i));
                if (type.hasError()) {
                    return ERROR;
                }
                argTypes.add(type);
            }
            return new FunType(returnType, argTypes);
        } else {
            return ERROR;
        }
    }

    public Type getLowerTypeBound(Type type1, Type type2) {
        if (type1.isFuncType() || type2.isFuncType()) {
            if (!type1.isFuncType() || !type2.isFuncType()) {
                return ERROR;
            }

            if (((FunType) type1).argTypes.size() != ((FunType) type2).argTypes.size()) {
                return ERROR;
            }
            var returnType = getLowerTypeBound(((FunType) type1).returnType, ((FunType) type2).returnType);
            if (returnType.hasError()) {
                return ERROR;
            }
            List<Type> argTypes = new ArrayList<>();
            for (var i = 0; i < ((FunType) type1).argTypes.size(); i++) {
                var type = getUpperTypeBound(((FunType) type1).argTypes.get(i), ((FunType) type2).argTypes.get(i));
                if (type.hasError()) {
                    return ERROR;
                }
                argTypes.add(type);
            }
            return new FunType(returnType, argTypes);
        } else if (type1.subtypeOf(type2)) {
            return type1;
        } else if(type2.subtypeOf(type1)) {
            return type2;
        } else {
            return ERROR;
        }
    }
}