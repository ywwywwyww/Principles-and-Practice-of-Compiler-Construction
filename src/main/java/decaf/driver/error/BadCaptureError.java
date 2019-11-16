package decaf.driver.error;

import decaf.frontend.tree.Pos;

/**
 * 不能对捕获的外层的非类作用域中的符号直接赋值，但如果传入的是一个对象或数组的引用，可以通过该引用修改类的成员或数组元素。
 */
public class BadCaptureError extends DecafError {

    public BadCaptureError(Pos pos) {
        super(pos);
    }

    @Override
    protected String getErrMsg() {
        return "cannot assign value to captured variables in lambda expression";
    }

}
