package decaf.frontend.scope;

import decaf.frontend.symbol.ClassSymbol;
import decaf.frontend.symbol.LambdaSymbol;
import decaf.frontend.symbol.MethodSymbol;
import decaf.frontend.symbol.Symbol;
import decaf.frontend.tree.Pos;

import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.function.Predicate;

/**
 * A symbol table, which is organized as a stack of scopes, maintained by {@link decaf.frontend.typecheck.Namer}.
 * <p>
 * A typical full scope stack looks like the following:
 * <pre>
 *     LocalScope   --- stack top (current scope)
 *     ...          --- many nested local scopes
 *     LocalScope
 *     FormalScope
 *     ClassScope
 *     ...          --- many parent class scopes
 *     ClassScope
 *     GlobalScope  --- stack bottom
 * </pre>
 * Make sure the global scope is always at the bottom, and NO class scope appears in neither formal nor local scope.
 *
 * @see Scope
 */
public class ScopeStack {

    /**
     * The global scope.
     */
    public final GlobalScope global;

    public ScopeStack(GlobalScope global) {
        this.global = global;
    }

    /**
     * The current scope, at the stack top.
     *
     * @return current scope
     */
    public Scope currentScope() {
        if (scopeStack.isEmpty()) return global;
        return scopeStack.peek();
    }

    /**
     * The innermost (top most on stack) class we now locate in.
     *
     * @return class symbol
     */
    public ClassSymbol currentClass() {
        Objects.requireNonNull(currClass);
        return currClass;
    }

    /**
     * The method we now locate in.
     *
     * @return method symbol
     */
    public Symbol currentMethod() {
        Objects.requireNonNull(currMethod.peek());
        return currMethod.peek();
    }

    public MethodSymbol currentMemberMethod() {
        Objects.requireNonNull(currMemberMethod);
        return currMemberMethod;
    }

    public Optional<LambdaSymbol> currentLambda() {
        if (currLambda.empty())
            return Optional.empty();
        return Optional.ofNullable(currLambda.peek());
    }

    /**
     * Open a scope.
     * <p>
     * If the current scope is a class scope, then we must first push all its super classes and then push this.
     * Otherwise, only push the `scope`.
     * <p>
     * REQUIRES: you don't open multiple class scopes, and never open a class scope when the current scope is
     * a formal/local scope.
     */
    public void open(Scope scope) {
        assert !scope.isGlobalScope();
        if (scope.isClassScope()) {
            assert !currentScope().isFormalOrLocalScopeOrLambdaScope();
            var classScope = (ClassScope) scope;
            classScope.parentScope.ifPresent(this::open);
            currClass = classScope.getOwner();
        } else if (scope.isFormalScope()) {
            var formalScope = (FormalScope) scope;
            currMethod.push(formalScope.getOwner());
            currMemberMethod = formalScope.getOwner();
        } else if (scope.isLambdaScope()) {
            var lambdaScope = (LambdaScope) scope;
            currLambda.push(lambdaScope.getOwner());
            currMethod.push(lambdaScope.getOwner());
        }
        scopeStack.push(scope);
    }

    /**
     * Close the current scope.
     * <p>
     * If the current scope is a class scope, then we must close this class and all super classes. Since the global
     * scope is never pushed to the actual {@code scopeStack}, we need to pop all scopes!
     * Otherwise, only pop the current scope.
     */
    public void close() {
        assert !scopeStack.isEmpty();
        Scope scope = scopeStack.pop();
        if (scope.isClassScope()) {
            while (!scopeStack.isEmpty()) {
                scopeStack.pop();
            }
        } else if(scope.isLambdaScope()) {
            currLambda.pop();
            currMethod.pop();
        } else if(scope.isFormalScope()) {
            currMethod.pop();
            currMemberMethod = null;
        }
    }

    /**
     * Lookup a symbol by name. By saying "lookup", the user expects that the symbol is found.
     * In this way, we will always search in all possible scopes and returns the innermost result.
     *
     * @param key symbol's name
     * @return innermost found symbol (if any)
     */
    public Optional<Symbol> lookup(String key) {
        return findWhile(key, whatever -> true, whatever -> true);
    }

    /**
     * Same with {@link #lookup} but we restrict the symbol's position to be before the given {@code pos}.
     *
     * @param key symbol's name
     * @param pos position
     * @return innermost found symbol before {@code pos} (if any)
     */
    public Optional<Symbol> lookupBefore(String key, Pos pos) {
        return findWhile(key, whatever -> true, s -> !(s.domain().isLocalScope() && s.pos.compareTo(pos) >= 0));
    }

    public Optional<Symbol> lookupBeforeForLambda(String key, Pos pos) {
        return findWhile(key, whatever -> true,
                s -> !((s.domain().isLocalScope() || s.domain().isLambdaScope() || s.domain().isFormalScope()) &&
                        s.pos.compareTo(pos) >= 0));
    }

    /**
     * Find if a symbol is conflicting with some already defined symbol. Rules:
     * First, if the current scope is local scope or formal scope, then it cannot conflict with any already defined
     * symbol till the formal scope, and it cannot conflict with any names in the global scope.
     * <p>
     * Second, if the current scope is class scope or global scope, then it cannot conflict with any already defined
     * symbol.
     * <p>
     * NO override checking is issued here -- the type checker is in charge of this!
     *
     * @param key symbol's name
     * @return innermost conflicting symbol (if any)
     */
    public Optional<Symbol> findConflict(String key) {
        if (currentScope().isFormalOrLocalScopeOrLambdaScope())
            return findWhile(key, Scope::isFormalOrLocalScopeOrLambdaScope, whatever -> true).or(() -> global.find(key));
        return lookup(key);
    }

    /**
     * Tell if a class is already defined in the global scope.
     *
     * @param key class's name
     * @return true/false
     */
    public boolean containsClass(String key) {
        return global.containsKey(key);
    }

    /**
     * Lookup a class in the global scope.
     *
     * @param key class's name
     * @return class symbol (if found)
     */
    public Optional<ClassSymbol> lookupClass(String key) {
        return Optional.ofNullable(global.getClass(key));
    }

    /**
     * Get a class from global scope.
     *
     * @param key class's name
     * @return class symbol (if found) or null (if not found)
     */
    public ClassSymbol getClass(String key) {
        return global.getClass(key);
    }

    /**
     * Declare a symbol in the current scope.
     *
     * @param symbol symbol
     * @see Scope#declare
     */
    public void declare(Symbol symbol) {
//        if (symbol.type.isVarType())
//        {
//            System.err.println("Declare VARTYPE Var in Scope");
//        }
        currentScope().declare(symbol);
    }

    public void redeclare(Symbol symbol) {
        currentScope().redeclare(symbol);
    }

    public void undeclare(Symbol symbol) {
        currentScope().undeclare(symbol);
    }

    // Look up symbol in the scope stack and capture this symbol in lambda scope
    public void capture(Symbol symbol) {
//        System.err.printf("try to capture %s in %s\n", symbol, currentScope());
        ListIterator<Scope> iter = scopeStack.listIterator(scopeStack.size());
        while (iter.hasPrevious()) {
            var scope = iter.previous();
            if (symbol.definedIn == scope) {
                return;
            }
            if (scope.isLambdaScope()) {
                if (((LambdaScope) scope).capturedSymbol.contains(symbol)) {
                    return;
                }
                ((LambdaScope) scope).capturedSymbol.add(symbol);
            }
        }
    }

    private Stack<Scope> scopeStack = new Stack<>();
    private ClassSymbol currClass;
    private MethodSymbol currMemberMethod;
    private Stack<Symbol> currMethod = new Stack<>();
    private Stack<LambdaSymbol> currLambda = new Stack<>();

    private Optional<Symbol> findWhile(String key, Predicate<Scope> cond, Predicate<Symbol> validator) {
        ListIterator<Scope> iter = scopeStack.listIterator(scopeStack.size());
        while (iter.hasPrevious()) {
            var scope = iter.previous();
            if (!cond.test(scope)) return Optional.empty();
            var symbol = scope.find(key);
//            System.err.printf("%s\n", key);
            if (symbol.isPresent() && validator.test(symbol.get())) return symbol;
        }
        return cond.test(global) ? global.find(key) : Optional.empty();
    }
}
