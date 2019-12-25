package decaf.backend.dataflow;

import decaf.backend.dataflow.flow.DataFlow;
import decaf.lowlevel.instr.PseudoInstr;

/**
 * A program location in a basic block, i.e. instruction with results of liveness analysis.
 */
public class Loc<I extends PseudoInstr> {
    public I instr;
    public DataFlow dataFlow = new DataFlow();

    Loc(I instr) {
        this.instr = instr;
    }
}
