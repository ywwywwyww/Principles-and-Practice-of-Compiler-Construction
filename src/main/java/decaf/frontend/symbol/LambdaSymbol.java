package decaf.frontend.symbol;

import decaf.frontend.scope.FormalScope;
import decaf.frontend.scope.LambdaScope;
import decaf.frontend.tree.Pos;
import decaf.frontend.tree.Tree;
import decaf.frontend.type.FunType;
import decaf.frontend.type.Type;

public final class LambdaSymbol extends Symbol {

    public Type type;

    public final LambdaScope scope;

    public LambdaSymbol(Type type, LambdaScope scope, Pos pos) {
        super("lambda@"+pos.toString(), type, pos);
        this.type = type;
        this.scope = scope;
        scope.setOwner(this);
    }

    @Override
    protected String str() {
        return String.format("function lambda@%s : %s", pos, type);
    }

    @Override
    public boolean isMember() {
        return definedIn.isClassScope();
    }

    @Override
    public boolean isLambdaSymbol() {
        return true;
    }
}
