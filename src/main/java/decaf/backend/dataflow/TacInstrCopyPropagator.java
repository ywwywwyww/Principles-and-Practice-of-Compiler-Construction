package decaf.backend.dataflow;


import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.Map;

class TacInstrCopyPropagator implements TacInstr.Visitor {
    Map<Temp, Temp> copy;

    TacInstr resInstr;

    boolean changed;

    @Override
    public void visitAssign(TacInstr.Assign instr) {
        resInstr = new TacInstr.Assign(instr.dst, find(instr.src));
    }

    @Override
    public void visitUnary(TacInstr.Unary instr) {
        resInstr = new TacInstr.Unary(instr.op, instr.dst, find(instr.operand));
    }

    @Override
    public void visitBinary(TacInstr.Binary instr) {
        resInstr = new TacInstr.Binary(instr.op, instr.dst, find(instr.lhs), find(instr.rhs));
    }

    @Override
    public void visitCondBranch(TacInstr.CondBranch instr) {
        resInstr = new TacInstr.CondBranch(instr.op, find(instr.cond), instr.target);
    }

    @Override
    public void visitParm(TacInstr.Parm instr) {
        resInstr = new TacInstr.Parm(find(instr.value));
    }

    @Override
    public void visitMemory(TacInstr.Memory instr) {
        resInstr = new TacInstr.Memory(instr.op,
                instr.op == TacInstr.Memory.Op.STORE ? find(instr.dst) : instr.dst, find(instr.base), instr.offset);
    }

    @Override
    public void visitReturn(TacInstr.Return instr) {
        resInstr = instr.value.map(temp -> new TacInstr.Return(find(temp))).orElseGet(TacInstr.Return::new);
    }

    @Override
    public void visitIndirectCall(TacInstr.IndirectCall instr) {
        resInstr = instr.dst.map(temp -> new TacInstr.IndirectCall(temp, find(instr.entry))).orElseGet(() -> new TacInstr.IndirectCall(find(instr.entry)));
    }

    @Override
    public void visitOthers(TacInstr instr) {
        if (!instr.getRead().isEmpty()) {
            System.err.printf("ERROR: %s\n", instr);
        }
        resInstr = instr;
    }

    Temp find(Temp val) {
        while(copy.containsKey(val)) {
            val = copy.get(val);
            changed = true;
        }
        return val;
    }
}