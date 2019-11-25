package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：cannot declare identifier 'boost' as void type<br>
 * PA2
 */
public class BadParamTypeError extends DecafError {

    public BadParamTypeError(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "arguments in function type must be non-void known type";
    }

}
