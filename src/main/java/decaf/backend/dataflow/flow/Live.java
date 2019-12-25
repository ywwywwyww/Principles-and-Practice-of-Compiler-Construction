package decaf.backend.dataflow.flow;

import decaf.lowlevel.instr.Temp;

import java.util.Set;

public class Live {
    public Set<Temp> in;
    public Set<Temp> out;
    public Set<Temp> def;
    public Set<Temp> use;
}