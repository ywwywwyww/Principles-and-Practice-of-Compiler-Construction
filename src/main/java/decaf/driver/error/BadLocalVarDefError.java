package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：class 'zig' not found<br>
 * PA2
 */
public class BadLocalVarDefError extends DecafError {

    private String name;

    public BadLocalVarDefError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "cannot define variable '" + name + "' in update statement";
    }

}
