package decaf.backend.dataflow;

import decaf.lowlevel.tac.TacInstr;

public class CopyPropagator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        return false;
    }
}
