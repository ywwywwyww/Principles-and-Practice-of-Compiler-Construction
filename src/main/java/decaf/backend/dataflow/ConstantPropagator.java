package decaf.backend.dataflow;

import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.Map;
import java.util.TreeMap;

/**
 * Perform constant folding and constant propagation on a control flow graph.
 */
public class ConstantPropagator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        (new LivenessAnalyzer<TacInstr>()).accept(graph);

        for (var bb : graph.nodes) {
            bb.dataFlow.val.in = new TreeMap<>();
        }

        for (int i = 0; i < graph.numArgs; i++) {
            graph.getBlock(0).dataFlow.val.in.put(new Temp(i), Constant.NAC);
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            // Optimize each basic block
            for (var bb : graph.nodes) {
                analyzeFor(bb);
            }

            for (var bb : graph.nodes) {
                for (var last : graph.getPrev(bb.id)) {
                    for (var temp : graph.getBlock(last).dataFlow.val.out.keySet()) {
                        if (bb.dataFlow.val.in.containsKey(temp)) {
                            var temp2 = merge(bb.dataFlow.val.in.get(temp), graph.getBlock(last).dataFlow.val.out.get(temp));
                            if (!(temp2.equals(bb.dataFlow.val.in.get(temp)))) {
                                bb.dataFlow.val.in.replace(temp, temp2);
                                changed = true;
                            }
                        } else {
                            bb.dataFlow.val.in.put(temp, graph.getBlock(last).dataFlow.val.out.get(temp));
                            changed = true;
                        }
                    }
                }
            }
        }

        boolean success = false;

        for (var bb : graph.nodes) {
            if (optimizeFor(bb)) {
                success = true;
            }
        }

        return success;
    }

    void analyzeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Constant> val = new TreeMap<>(bb.dataFlow.val.in);
        var it = bb.forwardIterator();
        cp.val = val;
        while (it.hasNext()) {
            var loc = it.next();
            loc.dataFlow.val.in = new TreeMap<>(val);
            loc.instr.accept(cp);
            loc.dataFlow.val.out = new TreeMap<>(val);
        }
        bb.dataFlow.val.out = new TreeMap<>(val);
    }

    boolean optimizeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Constant> val = new TreeMap<>(bb.dataFlow.val.in);
        var it = bb.forwardIterator();
        cp.changed = false;
        cp.val = val;
        while (it.hasNext()) {
            var loc = it.next();
            loc.dataFlow.val.in = new TreeMap<>(val);
            loc.instr.accept(cp);
            loc.instr = cp.resInstr;
            loc.dataFlow.val.out = new TreeMap<>(val);
        }
        bb.dataFlow.val.out = new TreeMap<>(val);
        return cp.changed;
    }

    TacInstrConstantPropagator cp = new TacInstrConstantPropagator();

    Constant merge(Constant a, Constant b) {
        if (a.isVAL() && b.isVAL()) {
            if (a.val == b.val) {
                return a;
            } else {
                return Constant.NAC;
            }
        } else {
            return Constant.NAC;
        }
    }
}