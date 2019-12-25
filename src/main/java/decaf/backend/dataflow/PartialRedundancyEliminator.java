package decaf.backend.dataflow;

import decaf.lowlevel.tac.TacInstr;


/**
 * Perform partial redundancy elimination on a control flow graph.
 */
public class PartialRedundancyEliminator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        return false;
    }
}
