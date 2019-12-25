package decaf.backend.dataflow.flow;

import decaf.backend.dataflow.Constant;
import decaf.lowlevel.instr.Temp;

import java.util.Map;

public class Val {
    public Map<Temp, Constant> in;
    public Map<Temp, Constant> out;
}
