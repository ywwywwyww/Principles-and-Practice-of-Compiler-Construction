package decaf.backend.dataflow;

import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.Map;

public class TacInstrConstantPropagator implements TacInstr.Visitor {
    Map<Temp, Constant> val;
    boolean changed;
    TacInstr resInstr;

    @Override
    public void visitAssign(TacInstr.Assign instr) {
        resInstr = instr;
        if (val.containsKey(instr.src)) {
            if (val.get(instr.src).isVAL()) {
                val.put(instr.dst, val.get(instr.src));
                resInstr = new TacInstr.LoadImm4(instr.dst, val.get(instr.src).val);
                changed = true;
            } else {
                val.put(instr.dst, Constant.NAC);
            }
        }
    }

    @Override
    public void visitLoadImm4(TacInstr.LoadImm4 instr) {
        resInstr = instr;
        val.put(instr.dst, new Constant(instr.value));
    }

    @Override
    public void visitUnary(TacInstr.Unary instr) {
        resInstr = instr;
        if (val.containsKey(instr.operand)) {
            if (val.get(instr.operand).isVAL()) {
                int operand = val.get(instr.operand).val;
                int res = switch (instr.op) {
                    case NEG -> -operand;
                    case LNOT -> (operand == 0 ? 1 : 0);
                };
                val.put(instr.dst, new Constant(res));
                resInstr = new TacInstr.LoadImm4(instr.dst, res);
                changed = true;
            } else {
                val.put(instr.dst, Constant.NAC);
            }
        }
    }

    @Override
    public void visitBinary(TacInstr.Binary instr) {
        resInstr = instr;
        if (val.containsKey(instr.lhs) && val.containsKey(instr.rhs) && val.get(instr.lhs).isVAL() &&
                val.get(instr.rhs).isVAL()) {
            int lhs = val.get(instr.lhs).val;
            int rhs = val.get(instr.rhs).val;
            int res = switch (instr.op) {
                case ADD -> lhs + rhs;
                case SUB -> lhs - rhs;
                case MUL -> lhs * rhs;
                case DIV -> (rhs == 0 ? 0 : lhs / rhs);
                case MOD -> (rhs == 0 ? 0 : lhs % rhs);
                case EQU -> (lhs == rhs ? 1 : 0);
                case NEQ -> (lhs != rhs ? 1 : 0);
                case GEQ -> (lhs >= rhs ? 1 : 0);
                case GTR -> (lhs > rhs ? 1 : 0);
                case LEQ -> (lhs <= rhs ? 1 : 0);
                case LES -> (lhs < rhs ? 1 : 0);
                case LOR -> (lhs != 0 || rhs != 0 ? 1 : 0);
                case LAND -> (lhs != 0 && rhs != 0 ? 1 : 0);
            };
            val.put(instr.dst, new Constant(res));
            resInstr = new TacInstr.LoadImm4(instr.dst, res);
            changed = true;
        } else if (instr.lhs.equals(instr.rhs)) {
            int res = switch (instr.op) {
                case SUB -> 0;
                case DIV -> 1;
                case MOD -> 0;
                case EQU -> 1;
                case NEQ -> 0;
                case GEQ -> 1;
                case GTR -> 0;
                case LEQ -> 1;
                case LES -> 0;
                default -> -1;
            };
            if (res != -1) {
                val.put(instr.dst, new Constant(res));
                resInstr = new TacInstr.LoadImm4(instr.dst, res);
                changed = true;
            } else {
                val.put(instr.dst, Constant.NAC);
            }
        } else {
            switch (instr.op) {
                case ADD:
                    if (val.containsKey(instr.lhs) && val.get(instr.lhs).isVAL() && val.get(instr.lhs).val == 0) {
                        val.put(instr.dst, Constant.NAC);
                        resInstr = new TacInstr.Assign(instr.dst, instr.rhs);
                        changed = true;
                    } else if (val.containsKey(instr.rhs) && val.get(instr.rhs).isVAL() && val.get(instr.rhs).val == 0) {
                        val.put(instr.dst, Constant.NAC);
                        resInstr = new TacInstr.Assign(instr.dst, instr.lhs);
                        changed = true;
                    } else {
                        val.put(instr.dst, Constant.NAC);
                    }
                    break;
                case SUB:
                    if (val.containsKey(instr.lhs) && val.get(instr.lhs).isVAL() && val.get(instr.lhs).val == 0) {
                        val.put(instr.dst, Constant.NAC);
                        resInstr = new TacInstr.Unary(TacInstr.Unary.Op.NEG, instr.dst, instr.rhs);
                        changed = true;
                    } else if (val.containsKey(instr.rhs) && val.get(instr.rhs).isVAL() && val.get(instr.rhs).val == 0) {
                        val.put(instr.dst, Constant.NAC);
                        resInstr = new TacInstr.Assign(instr.dst, instr.lhs);
                        changed = true;
                    } else {
                        val.put(instr.dst, Constant.NAC);
                    }
                    break;
                case MUL:
                    if (val.containsKey(instr.lhs) && val.get(instr.lhs).isVAL() && val.get(instr.lhs).val == 0) {
                        val.put(instr.dst, new Constant(0));
                        resInstr = new TacInstr.LoadImm4(instr.dst, 0);
                        changed = true;
                    } else if (val.containsKey(instr.rhs) && val.get(instr.rhs).isVAL() && val.get(instr.rhs).val == 0) {
                        val.put(instr.dst, new Constant(0));
                        resInstr = new TacInstr.LoadImm4(instr.dst, 0);
                        changed = true;
                    } else {
                        val.put(instr.dst, Constant.NAC);
                    }
                    break;
                case LAND:
                    if (val.containsKey(instr.lhs) && val.get(instr.lhs).isVAL()) {
                        if (val.get(instr.lhs).val == 0) {
                            val.put(instr.dst, new Constant(0));
                            resInstr = new TacInstr.LoadImm4(instr.dst, 0);
                            changed = true;
                        } else {
                            val.put(instr.dst, Constant.NAC);
                            resInstr = new TacInstr.Assign(instr.dst, instr.rhs);
                            changed = true;
                        }
                    } else if (val.containsKey(instr.rhs) && val.get(instr.rhs).isVAL()) {
                        if (val.get(instr.rhs).val == 0) {
                            val.put(instr.dst, new Constant(0));
                            resInstr = new TacInstr.LoadImm4(instr.dst, 0);
                            changed = true;
                        } else {
                            val.put(instr.dst, Constant.NAC);
                            resInstr = new TacInstr.Assign(instr.dst, instr.lhs);
                            changed = true;
                        }
                    } else {
                        val.put(instr.dst, Constant.NAC);
                    }
                    break;
                case LOR:
                    if (val.containsKey(instr.lhs) && val.get(instr.lhs).isVAL()) {
                        if (val.get(instr.lhs).val == 0) {
                            val.put(instr.dst, Constant.NAC);
                            resInstr = new TacInstr.Assign(instr.dst, instr.rhs);
                            changed = true;
                        } else {
                            val.put(instr.dst, new Constant(1));
                            resInstr = new TacInstr.LoadImm4(instr.dst, 1);
                            changed = true;
                        }
                    } else if (val.containsKey(instr.rhs) && val.get(instr.rhs).isVAL()) {
                        if (val.get(instr.rhs).val == 0) {
                            val.put(instr.dst, Constant.NAC);
                            resInstr = new TacInstr.Assign(instr.dst, instr.lhs);
                            changed = true;
                        } else {
                            val.put(instr.dst, new Constant(1));
                            resInstr = new TacInstr.LoadImm4(instr.dst, 1);
                            changed = true;
                        }
                    } else {
                        val.put(instr.dst, Constant.NAC);
                    }
                    break;
                default:
                    val.put(instr.dst, Constant.NAC);
            }
        }
    }

    @Override
    public void visitCondBranch(TacInstr.CondBranch instr) {
        resInstr = instr;
        if (val.containsKey(instr.cond)) {
            if (val.get(instr.cond).isVAL()) {
                int cond = val.get(instr.cond).val;
                boolean res = switch (instr.op) {
                    case BEQZ -> cond == 0;
                    case BNEZ -> cond != 0;
                };
                if (res) {
                    resInstr = new TacInstr.Branch(instr.target);
                } else {
                    resInstr = new TacInstr.Memo("This instruction has been deleted.");
                }
            }
        }
    }

    @Override
    public void visitOthers(TacInstr instr) {
        resInstr = instr;
//                if (!loc.instr.getRead().isEmpty()) {
//                    System.err.printf("ERROR: %s\n", loc.instr);
//                }
        var written = instr.getWritten();
        for (var temp : written) {
            val.put(temp, Constant.NAC);
        }
    }
}
