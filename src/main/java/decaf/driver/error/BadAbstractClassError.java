package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * 若一个类中含有抽象成员，或者它继承了一个抽象类但没有重写所有的抽象方法，那么该类必须声明为抽象类。
 */
public class BadAbstractClassError extends DecafError {

    private String name;

    public BadAbstractClassError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "'" + name + "' is not abstract and does not override all abstract methods";
    }

}
