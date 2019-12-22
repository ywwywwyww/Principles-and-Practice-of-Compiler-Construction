package decaf.backend.dataflow;

import decaf.lowlevel.instr.PseudoInstr;

import java.util.function.Consumer;

public interface CFGOptimizer<I extends PseudoInstr>{
    /**
     * @param graph CFG graph
     * @return Whether it is optimized
     */
    boolean optimize(CFG<I> graph);
}
