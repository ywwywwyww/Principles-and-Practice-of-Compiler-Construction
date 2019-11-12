package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * 抽象类不能使用 new 进行实例化。
 */
public class InstantiateAbstractClassError extends DecafError {

    private String name;

    public InstantiateAbstractClassError(Pos pos, String name) {
        super(pos);
        this.name = name;
    }

    @Override
    protected String getErrMsg() {
        return "cannot instantiate abstract class '" + name + "'";
    }

}
