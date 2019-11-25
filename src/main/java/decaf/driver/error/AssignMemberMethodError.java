package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * example：declaration of 'abcde' here conflicts with earlier declaration at (3,2)<br>
 * PA2
 */
public class AssignMemberMethodError extends DecafError {

    private String name;

    public AssignMemberMethodError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to class member method '" + name + "'";
    }

}
