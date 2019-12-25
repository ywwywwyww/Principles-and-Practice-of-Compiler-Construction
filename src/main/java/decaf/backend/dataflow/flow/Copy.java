package decaf.backend.dataflow.flow;

import decaf.lowlevel.instr.Temp;

import java.util.Map;
import java.util.Set;

public class Copy {
    public Map<Temp, Temp> in;
    public Map<Temp, Temp> out;
    public Map<Temp, Temp> gen;
    public Set<Temp> kill;
}
