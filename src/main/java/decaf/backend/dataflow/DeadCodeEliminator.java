package decaf.backend.dataflow;

import decaf.lowlevel.tac.TacInstr;

import java.util.*;

/**
 * Perform dead code elimination on a control flow graph.
 */
public class DeadCodeEliminator implements CFGOptimizer<TacInstr> {

    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        boolean success = false;
        for (var bb : graph.nodes) {
            computeDefAndLiveUseFor(bb);
            bb.dataFlow.live.in = new TreeSet<>();
            bb.dataFlow.live.in.addAll(bb.dataFlow.live.use);
            bb.dataFlow.live.out = new TreeSet<>();
        }

        var changed = true;
        do {
            changed = false;
            for (var bb : graph.nodes) {
                for (var next : graph.getSucc(bb.id)) {
                    bb.dataFlow.live.out.addAll(graph.getBlock(next).dataFlow.live.in);
                }
                bb.dataFlow.live.out.removeAll(bb.dataFlow.live.def);
                if (bb.dataFlow.live.in.addAll(bb.dataFlow.live.out)) {
                    changed = true;
                }
                for (var next : graph.getSucc(bb.id)) {
                    bb.dataFlow.live.out.addAll(graph.getBlock(next).dataFlow.live.in);
                }
            }
        } while (changed);

        for (var bb : graph.nodes) {
            if (analyzeLivenessForEachLocIn(bb)) {
                success = true;
            }
        }
        return success;
    }

    /**
     * Compute the {@code def} and {@code live.use} set for basic block {@code bb}.
     * <p>
     * Recall the definition:
     * - {@code def}: set of all variables (i.e. temps) that are assigned to a value. Thus, we simply union all the
     * written temps of every instruction.
     * - {@code live.use}: set of all variables (i.e. temps) that are used before they are assigned to a value in this
     * basic block. Note this is NOT simply equal to the union set all read temps, but only those are not yet
     * assigned/reassigned.
     *
     * @param bb basic block
     */
    private void computeDefAndLiveUseFor(BasicBlock<TacInstr> bb) {
        bb.dataFlow.live.def = new TreeSet<>();
        bb.dataFlow.live.use = new TreeSet<>();

        for (var loc : bb) {
            for (var read : loc.instr.getRead()) {
                if (!bb.dataFlow.live.def.contains(read)) {
                    // used before being assigned to a value
                    bb.dataFlow.live.use.add(read);
                }
            }
            bb.dataFlow.live.def.addAll(loc.instr.getWritten());
        }
    }

    /**
     * Perform liveness analysis for every single location in a basic block, so that we know at each program location,
     * which variables stay alive.
     * <p>
     * Idea: realizing that every location loc can be regarded as a "mini" basic block -- a block containing that
     * instruction solely, then the data flow equations also hold, and the situation becomes much simpler:
     * - loc.live.out = loc.next.live.in
     * - loc.def is simply the set of written temps
     * - loc.live.use is simply the set of read temps, since it is impossible to read and write a same temp
     * simultaneously
     * So you see, to back propagate every location solves the problem.
     *
     * @param bb the basic block
     */
    private boolean analyzeLivenessForEachLocIn(BasicBlock<TacInstr> bb) {
        List<Loc<TacInstr>> buf = new ArrayList<>();
        var liveOut = new TreeSet<>(bb.dataFlow.live.out);
        var it = bb.backwardIterator();
        boolean success = false;
        while (it.hasNext()) {
            var loc = it.next();
            loc.dataFlow.live.out = new TreeSet<>(liveOut);
            // Order is important here, because in an instruction, one temp can be both read and written, e.g.
            // in `_T1 = _T1 + _T2`, `_T1` must be alive before execution.
            liveOut.removeAll(loc.instr.getWritten());
            liveOut.addAll(loc.instr.getRead());
            loc.dataFlow.live.in = new TreeSet<>(liveOut);

            var written = loc.instr.getWritten();
            if (loc.instr instanceof TacInstr.Assign &&
                    ((TacInstr.Assign) loc.instr).src == ((TacInstr.Assign) loc.instr).dst) {
            } else if (!written.isEmpty() && !loc.dataFlow.live.out.containsAll(written)) {
                if (loc.instr instanceof TacInstr.DirectCall) {
                    var instr2 = new TacInstr.DirectCall(((TacInstr.DirectCall) loc.instr).entry);
                    var loc2 = new Loc<TacInstr>(instr2);
                    loc2.dataFlow.live.in = loc.dataFlow.live.in;
                    loc2.dataFlow.live.out = loc.dataFlow.live.out;
                    buf.add(loc2);
                } else if (loc.instr instanceof TacInstr.IndirectCall) {
                    var instr2 = new TacInstr.IndirectCall(((TacInstr.IndirectCall) loc.instr).entry);
                    var loc2 = new Loc<TacInstr>(instr2);
                    loc2.dataFlow.live.in = loc.dataFlow.live.in;
                    loc2.dataFlow.live.out = loc.dataFlow.live.out;
                    buf.add(loc2);
                }
                success = true;
//                System.err.printf("success\n");
            } else {
                buf.add(loc);
            }
        }
        Collections.reverse(buf);
        bb.locs = buf;
        // assert live.in == bb.live.in
        return success;
    }
}
