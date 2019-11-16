package decaf.frontend.scope;

import decaf.frontend.symbol.LambdaSymbol;

import java.util.Optional;

public class LambdaScope extends Scope {

    public LambdaScope(Scope parent) {
        super(Kind.FORMAL);
        assert parent.isFormalOrLocalScopeOrLambdaScope();
        this.parent = parent;
        if (parent.isLocalScope()){
            ((LocalScope) parent).nested.add(this);
        } else {
            ((LambdaScope) parent).setNested(this);
        }
    }

    public LambdaSymbol getOwner() {
        return owner;
    }

    public void setOwner(LambdaSymbol owner) {
        this.owner = owner;
    }

    @Override
    public boolean isLambdaScope() {
        return true;
    }

    /**
     * Get the local scope associated with the method body.
     *
     * @return local scope
     */
    public Optional<Scope> nestedLocalScope() {
        return Optional.ofNullable(nested);
    }

    /**
     * Set the local scope.
     *
     * @param scope local scope
     */
    void setNested(Scope scope) {
        nested = scope;
    }

    public LambdaSymbol owner;

    public Scope nested;

    public Scope parent;

}
