package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：test expression must have bool type<br>
 * PA2
 */
public class IncompatReturnTypesError extends DecafError {

    public IncompatReturnTypesError(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "incompatible return types in blocked expression";
    }

}
