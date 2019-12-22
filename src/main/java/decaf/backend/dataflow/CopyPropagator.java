package decaf.backend.dataflow;

import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.*;

public class CopyPropagator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        boolean success = false;

        for (var bb : graph.nodes) {
            analyzeFor(bb);
            bb.copyIn = new TreeMap<>();
            if (bb.id != 0) {
                for (int i = 0; i < graph.tempUsed; i++) {
                    bb.copyIn.put(new Temp(i), new Temp(-1));
                }
            }
            bb.copyOut = new TreeMap<>(bb.copyGen);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (var bb : graph.nodes) {
                if (bb.id != 0) {
                    for (var last : graph.getPrev(bb.id)) {
                        for (var key : new TreeSet<>(bb.copyIn.keySet())) {
                            if (!graph.getBlock(last).copyOut.containsKey(key) ||
                                    graph.getBlock(last).copyOut.get(key) != bb.copyIn.get(key)) {
                                bb.copyIn.remove(key);
                            }
                        }
                    }
                }
                Map<Temp, Temp> temp = new TreeMap<>(bb.copyIn);
                for (var key : bb.copyKill) {
                    temp.remove(key);
                }
                temp.putAll(bb.copyOut);
                if (!temp.equals(bb.copyOut)) {
                    bb.copyOut = temp;
                    changed = true;
                }
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
            bb.copyKill.addAll(loc.instr.getWritten());
            if (loc.instr instanceof TacInstr.Assign) {
                if (((TacInstr.Assign) loc.instr).src != ((TacInstr.Assign) loc.instr).dst) {
                    bb.copyGen.put(((TacInstr.Assign) loc.instr).dst, ((TacInstr.Assign) loc.instr).src);
                }
            }
        }
    }

    boolean optimizeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Temp> copy = new TreeMap<>(bb.copyIn);
        cp.copy = copy;
        cp.changed = false;
        var it = bb.forwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            loc.copyIn = new TreeMap<>(copy);
            cp.res = null;
            loc.instr.accept(cp);
            loc.instr = cp.res;
            if (loc.instr instanceof TacInstr.Assign) {
                copy.put(((TacInstr.Assign) loc.instr).dst, ((TacInstr.Assign) loc.instr).src);
//                System.err.println(loc.instr);
            }
            for (var key : new TreeSet<>(copy.keySet())) {
                if (loc.instr.getWritten().contains(copy.get(key))) {
                    copy.remove(key);
                }
            }
            loc.copyOut = new TreeMap<>(copy);
        }
        return cp.changed;
    }

    TacInstrCopyPropagator cp = new TacInstrCopyPropagator();
}

class TacInstrCopyPropagator implements TacInstr.Visitor {
    Map<Temp, Temp> copy;

    TacInstr res;

    boolean changed;

    @Override
    public void visitAssign(TacInstr.Assign instr) {
        res = new TacInstr.Assign(instr.dst, find(instr.src));
    }

    @Override
    public void visitUnary(TacInstr.Unary instr) {
        res = new TacInstr.Unary(instr.op, instr.dst, find(instr.operand));
    }

    @Override
    public void visitBinary(TacInstr.Binary instr) {
        res = new TacInstr.Binary(instr.op, instr.dst, find(instr.lhs), find(instr.rhs));
    }

    @Override
    public void visitCondBranch(TacInstr.CondBranch instr) {
        res = new TacInstr.CondBranch(instr.op, find(instr.cond), instr.target);
    }

    @Override
    public void visitParm(TacInstr.Parm instr) {
        res = new TacInstr.Parm(instr.value);
    }

    @Override
    public void visitMemory(TacInstr.Memory instr) {
        res = new TacInstr.Memory(instr.op,
                instr.op == TacInstr.Memory.Op.STORE ? find(instr.dst) : instr.dst, find(instr.base), instr.offset);
    }

    @Override
    public void visitReturn(TacInstr.Return instr) {
        res = instr.value.map(temp -> new TacInstr.Return(find(temp))).orElseGet(TacInstr.Return::new);
    }

    @Override
    public void visitOthers(TacInstr instr) {
        res = instr;
    }

    Temp find(Temp val) {
        while(copy.containsKey(val)) {
            val = copy.get(val);
            changed = true;
        }
        return val;
    }
};