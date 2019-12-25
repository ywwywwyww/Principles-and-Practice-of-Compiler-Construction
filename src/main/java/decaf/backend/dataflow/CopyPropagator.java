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
            bb.dataFlow.copy.in = new TreeMap<>();
            if (bb.id != 0) {
                for (int i = 0; i < graph.tempUsed; i++) {
                    bb.dataFlow.copy.in.put(new Temp(i), new Temp(-1));
                }
            } else {
                for (int i = graph.numArgs; i < graph.tempUsed; i++) {
                    bb.dataFlow.copy.in.put(new Temp(i), new Temp(-1));
                }
            }
            bb.dataFlow.copy.out = new TreeMap<>(bb.dataFlow.copy.in);
            for (var key : bb.dataFlow.copy.kill) {
                bb.dataFlow.copy.out.remove(key);
            }
            for (var key : new TreeSet<>(bb.dataFlow.copy.out.keySet())) {
                if (bb.dataFlow.copy.kill.contains(bb.dataFlow.copy.out.get(key))) {
                    bb.dataFlow.copy.out.remove(key);
                }
            }
            bb.dataFlow.copy.out.putAll(bb.dataFlow.copy.gen);
//            System.err.printf("%d.copy.gen: %s\n", bb.id, bb.copy.gen);
//            System.err.printf("%d.copy.kill: %s\n", bb.id, bb.copy.kill);
//            System.err.printf("%d.copy.in: %s\n", bb.id, bb.copy.in);
//            System.err.printf("%d.copy.out: %s\n", bb.id, bb.copy.out);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
//            System.err.printf("\n\n\n");
            for (var bb : graph.nodes) {
                if (bb.id != 0) {
                    for (var last : graph.getPrev(bb.id)) {
                        for (var key : new TreeSet<>(bb.dataFlow.copy.in.keySet())) {
//                            if (key.index == 2) {
//                                System.err.printf("%s %s %s\n", bb.copy.in.get(key), graph.getBlock(last).copy.out.get(key),
//                                        bb.copy.in.get(key).equals(graph.getBlock(last).copy.out.get(key)));
//                            }
                            if (graph.getBlock(last).dataFlow.copy.out.containsKey(key) && bb.dataFlow.copy.in.get(key).index == -1) {
                                bb.dataFlow.copy.in.put(key, graph.getBlock(last).dataFlow.copy.out.get(key));
//                                System.err.printf("hahaha1 %s\n", key);
                            } else if (!graph.getBlock(last).dataFlow.copy.out.containsKey(key) ||
                                    (graph.getBlock(last).dataFlow.copy.out.get(key).index != -1 &&
                                    !graph.getBlock(last).dataFlow.copy.out.get(key).equals(bb.dataFlow.copy.in.get(key)))) {
                                bb.dataFlow.copy.in.remove(key);
//                                System.err.printf("hahaha2 %s\n", key);
                            }
                        }
                    }
                }
                Map<Temp, Temp> temp = new TreeMap<>(bb.dataFlow.copy.in);
                for (var key : bb.dataFlow.copy.kill) {
                    temp.remove(key);
                }
                for (var key : new TreeSet<>(temp.keySet())) {
                    if (bb.dataFlow.copy.kill.contains(temp.get(key))) {
                        temp.remove(key);
                    }
                }
                temp.putAll(bb.dataFlow.copy.gen);
                if (!temp.equals(bb.dataFlow.copy.out)) {
                    bb.dataFlow.copy.out = temp;
                    changed = true;
                }
//                System.err.printf("%d.copy.in: %s\n", bb.id, bb.copy.in);
//                System.err.printf("%d.copy.out: %s\n", bb.id, bb.copy.out);
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
        bb.dataFlow.copy.kill = new TreeSet<>();
        bb.dataFlow.copy.gen = new TreeMap<>();
        var it = bb.backwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            if (loc.instr instanceof TacInstr.Assign) {
                if (!bb.dataFlow.copy.kill.contains(((TacInstr.Assign) loc.instr).dst) &&
                        !bb.dataFlow.copy.kill.contains(((TacInstr.Assign) loc.instr).src)) {
                    bb.dataFlow.copy.gen.put(((TacInstr.Assign) loc.instr).dst, ((TacInstr.Assign) loc.instr).src);
                }
            }
            bb.dataFlow.copy.kill.addAll(loc.instr.getWritten());
        }
    }

    boolean optimizeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Temp> copy = new TreeMap<>(bb.dataFlow.copy.in);
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
            loc.dataFlow.copy.in = new TreeMap<>(copy);
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
            loc.dataFlow.copy.out = new TreeMap<>(copy);
        }
        return cp.changed;
    }

    TacInstrCopyPropagator cp = new TacInstrCopyPropagator();
}