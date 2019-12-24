package decaf.backend.dataflow;

import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.*;

/**
 * Perform copy propagation on a control flow graph.
 */
public class CopyPropagator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        boolean success = false;

//        System.err.printf("\n\n%s\n", graph.funcLabel);

        for (var bb : graph.nodes) {
            analyzeFor(bb);
            bb.copyIn = new TreeMap<>();
            if (bb.id != 0) {
                for (int i = 0; i < graph.tempUsed; i++) {
                    bb.copyIn.put(new Temp(i), new Temp(-1));
                }
            }
            bb.copyOut = new TreeMap<>(bb.copyIn);
            for (var key : bb.copyKill) {
                bb.copyOut.remove(key);
            }
            for (var key : new TreeSet<>(bb.copyOut.keySet())) {
                if (bb.copyKill.contains(bb.copyOut.get(key))) {
                    bb.copyOut.remove(key);
                }
            }
            bb.copyOut.putAll(bb.copyGen);
//            System.err.printf("%d.copyGen: %s\n", bb.id, bb.copyGen);
//            System.err.printf("%d.copyKill: %s\n", bb.id, bb.copyKill);
//            System.err.printf("%d.copyIn: %s\n", bb.id, bb.copyIn);
//            System.err.printf("%d.copyOut: %s\n", bb.id, bb.copyOut);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
//            System.err.printf("\n\n\n");
            for (var bb : graph.nodes) {
                if (bb.id != 0) {
                    for (var last : graph.getPrev(bb.id)) {
                        for (var key : new TreeSet<>(bb.copyIn.keySet())) {
//                            if (key.index == 2) {
//                                System.err.printf("%s %s %s\n", bb.copyIn.get(key), graph.getBlock(last).copyOut.get(key),
//                                        bb.copyIn.get(key).equals(graph.getBlock(last).copyOut.get(key)));
//                            }
                            if (graph.getBlock(last).copyOut.containsKey(key) && bb.copyIn.get(key).index == -1) {
                                bb.copyIn.put(key, graph.getBlock(last).copyOut.get(key));
//                                System.err.printf("hahaha1 %s\n", key);
                            } else if (!graph.getBlock(last).copyOut.containsKey(key) ||
                                    (graph.getBlock(last).copyOut.get(key).index != -1 &&
                                    !graph.getBlock(last).copyOut.get(key).equals(bb.copyIn.get(key)))) {
                                bb.copyIn.remove(key);
//                                System.err.printf("hahaha2 %s\n", key);
                            }
                        }
                    }
                }
                Map<Temp, Temp> temp = new TreeMap<>(bb.copyIn);
                for (var key : bb.copyKill) {
                    temp.remove(key);
                }
                for (var key : new TreeSet<>(temp.keySet())) {
                    if (bb.copyKill.contains(temp.get(key))) {
                        temp.remove(key);
                    }
                }
                temp.putAll(bb.copyGen);
                if (!temp.equals(bb.copyOut)) {
                    bb.copyOut = temp;
                    changed = true;
                }
//                System.err.printf("%d.copyIn: %s\n", bb.id, bb.copyIn);
//                System.err.printf("%d.copyOut: %s\n", bb.id, bb.copyOut);
            }
        }

        for (var bb : graph.nodes) {
            if (optimizeFor(bb)) {
                success = true;
            }
        }
        return success;
    }

    void analyzeFor(BasicBlock<TacInstr> bb) {
        bb.copyKill = new TreeSet<>();
        bb.copyGen = new TreeMap<>();
        var it = bb.backwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            if (loc.instr instanceof TacInstr.Assign) {
                if (!bb.copyKill.contains(((TacInstr.Assign) loc.instr).dst) &&
                        !bb.copyKill.contains(((TacInstr.Assign) loc.instr).src)) {
                    bb.copyGen.put(((TacInstr.Assign) loc.instr).dst, ((TacInstr.Assign) loc.instr).src);
                }
            }
            bb.copyKill.addAll(loc.instr.getWritten());
        }
    }

    boolean optimizeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Temp> copy = new TreeMap<>(bb.copyIn);
        for (var key : new TreeSet<>(copy.keySet())) {
            if (copy.get(key).index == -1) {
                copy.remove(key);
            }
        }
        cp.copy = copy;
        cp.changed = false;
        var it = bb.forwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            loc.copyIn = new TreeMap<>(copy);
            cp.resInstr = null;
            loc.instr.accept(cp);
            loc.instr = cp.resInstr;
            for (var key : new TreeSet<>(copy.keySet())) {
                if (loc.instr.getWritten().contains(copy.get(key)) || loc.instr.getWritten().contains(key)) {
                    copy.remove(key);
                }
            }
            if (loc.instr instanceof TacInstr.Assign) {
                if (((TacInstr.Assign) loc.instr).dst != ((TacInstr.Assign) loc.instr).src) {
                    copy.put(((TacInstr.Assign) loc.instr).dst, ((TacInstr.Assign) loc.instr).src);
//                System.err.println(loc.instr);
                }
            }
            loc.copyOut = new TreeMap<>(copy);
        }
        return cp.changed;
    }

    TacInstrCopyPropagator cp = new TacInstrCopyPropagator();
}