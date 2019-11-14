package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * 如果一个表达式不是函数类型需要报错
 * example：string is not a callable type
 * PA2
 */
public class NotCallableTypeError extends DecafError {

    private String type;

    public NotCallableTypeError(String type, Pos pos) {
        super(pos);
        this.type = type;
    }

    @Override
    protected String getErrMsg() {
        return type + " is not a callable type";
    }

}
