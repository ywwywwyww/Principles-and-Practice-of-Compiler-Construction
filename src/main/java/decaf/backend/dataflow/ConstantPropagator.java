package decaf.backend.dataflow;

import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.tac.TacInstr;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Perform constant folding and constant propagation on a control flow graph.
 */
public class ConstantPropagator implements CFGOptimizer<TacInstr> {
    @Override
    public boolean optimize(CFG<TacInstr> graph) {
        (new LivenessAnalyzer<TacInstr>()).accept(graph);

        for (var bb : graph.nodes) {
            bb.valIn = new TreeMap<>();
        }

        for (int i = 0; i < graph.numArgs; i++) {
            graph.getBlock(0).valIn.put(new Temp(i), Constant.NAC);
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
                    for (var temp : graph.getBlock(last).valOut.keySet()) {
                        if (bb.valIn.containsKey(temp)) {
                            var temp2 = merge(bb.valIn.get(temp), graph.getBlock(last).valOut.get(temp));
                            if (!(temp2.equals(bb.valIn.get(temp)))) {
                                bb.valIn.replace(temp, temp2);
                                changed = true;
                            }
                        } else {
                            bb.valIn.put(temp, graph.getBlock(last).valOut.get(temp));
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
        Map<Temp, Constant> val = new TreeMap<>(bb.valIn);
        var it = bb.forwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            loc.valIn = new TreeMap<>(val);
            if (loc.instr instanceof TacInstr.Assign) {
                if (val.containsKey(((TacInstr.Assign) loc.instr).src)) {
                    if (val.get(((TacInstr.Assign) loc.instr).src).isVAL()) {
                        val.put(((TacInstr.Assign) loc.instr).dst, val.get(((TacInstr.Assign) loc.instr).src));
                    } else {
                        val.put(((TacInstr.Assign) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if(loc.instr instanceof TacInstr.Unary) {
                if (val.containsKey(((TacInstr.Unary) loc.instr).operand)) {
                    if (val.get(((TacInstr.Unary) loc.instr).operand).isVAL()) {
                        int operand = val.get(((TacInstr.Unary) loc.instr).operand).val;
                        int res = switch (((TacInstr.Unary) loc.instr).op) {
                            case NEG -> -operand;
                            case LNOT -> (operand == 0 ? 1 : 0);
                        };
                        val.put(((TacInstr.Unary) loc.instr).dst, new Constant(res));
                    } else {
                        val.put(((TacInstr.Unary) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if(loc.instr instanceof TacInstr.Binary) {
                if (val.containsKey(((TacInstr.Binary) loc.instr).lhs) &&
                        val.containsKey(((TacInstr.Binary) loc.instr).rhs)) {
                    if (val.get(((TacInstr.Binary) loc.instr).lhs).isVAL() &&
                            val.get(((TacInstr.Binary) loc.instr).rhs).isVAL()) {
                        int lhs = val.get(((TacInstr.Binary) loc.instr).lhs).val;
                        int rhs = val.get(((TacInstr.Binary) loc.instr).rhs).val;
                        int res = switch (((TacInstr.Binary) loc.instr).op) {
                            case ADD -> lhs + rhs;
                            case SUB -> lhs - rhs;
                            case MUL -> lhs * rhs;
                            case DIV -> (rhs == 0 ? 0 : lhs / rhs);
                            case MOD -> (rhs == 0 ? 0 : lhs % rhs);
                            case EQU -> (lhs == rhs ? 1 : 0);
                            case GEQ -> (lhs >= rhs ? 1 : 0);
                            case GTR -> (lhs > rhs ? 1 : 0);
                            case LEQ -> (lhs <= rhs ? 1 : 0);
                            case LES -> (lhs < rhs ? 1 : 0);
                            case NEQ -> (lhs != rhs ? 1 : 0);
                            case LOR -> (lhs != 0 || rhs != 0 ? 1 : 0);
                            case LAND -> (lhs != 0 && rhs != 0 ? 1 : 0);
                        };
                        val.put(((TacInstr.Binary) loc.instr).dst, new Constant(res));
                    } else {
                        val.put(((TacInstr.Binary) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if (loc.instr instanceof TacInstr.LoadImm4) {
                val.put(((TacInstr.LoadImm4) loc.instr).dst, new Constant(((TacInstr.LoadImm4) loc.instr).value));
            } else {
                var written = loc.instr.getWritten();
                for (var temp : written) {
                    val.put(temp, Constant.NAC);
                }
            }
            loc.valOut = new TreeMap<>(val);
        }
        bb.valOut = new TreeMap<>(val);
    }

    boolean optimizeFor(BasicBlock<TacInstr> bb) {
        Map<Temp, Constant> val = new TreeMap<>(bb.valIn);
        boolean changed = false;
        var it = bb.forwardIterator();
        while (it.hasNext()) {
            var loc = it.next();
            loc.valIn = new TreeMap<>(val);
            if (loc.instr instanceof TacInstr.Assign) {
                if (val.containsKey(((TacInstr.Assign) loc.instr).src)) {
                    if (val.get(((TacInstr.Assign) loc.instr).src).isVAL()) {
                        val.put(((TacInstr.Assign) loc.instr).dst, val.get(((TacInstr.Assign) loc.instr).src));
                        loc.instr = new TacInstr.LoadImm4(((TacInstr.Assign) loc.instr).dst,
                                val.get(((TacInstr.Assign) loc.instr).src).val);
                        changed = true;
                    } else {
                        val.put(((TacInstr.Assign) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if(loc.instr instanceof TacInstr.Unary) {
                if (val.containsKey(((TacInstr.Unary) loc.instr).operand)) {
                    if (val.get(((TacInstr.Unary) loc.instr).operand).isVAL()) {
                        int operand = val.get(((TacInstr.Unary) loc.instr).operand).val;
                        int res = switch (((TacInstr.Unary) loc.instr).op) {
                            case NEG -> -operand;
                            case LNOT -> (operand == 0 ? 1 : 0);
                        };
                        val.put(((TacInstr.Unary) loc.instr).dst, new Constant(res));
                        loc.instr = new TacInstr.LoadImm4(((TacInstr.Unary) loc.instr).dst,
                                res);
                        changed = true;
                    } else {
                        val.put(((TacInstr.Unary) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if(loc.instr instanceof TacInstr.Binary) {
                if (val.containsKey(((TacInstr.Binary) loc.instr).lhs) &&
                        val.containsKey(((TacInstr.Binary) loc.instr).rhs)) {
                    if (val.get(((TacInstr.Binary) loc.instr).lhs).isVAL() &&
                            val.get(((TacInstr.Binary) loc.instr).rhs).isVAL()) {
                        int lhs = val.get(((TacInstr.Binary) loc.instr).lhs).val;
                        int rhs = val.get(((TacInstr.Binary) loc.instr).rhs).val;
                        int res = switch (((TacInstr.Binary) loc.instr).op) {
                            case ADD -> lhs + rhs;
                            case SUB -> lhs - rhs;
                            case MUL -> lhs * rhs;
                            case DIV -> (rhs == 0 ? 0 : lhs / rhs);
                            case MOD -> (rhs == 0 ? 0 : lhs % rhs);
                            case EQU -> (lhs == rhs ? 1 : 0);
                            case GEQ -> (lhs >= rhs ? 1 : 0);
                            case GTR -> (lhs > rhs ? 1 : 0);
                            case LEQ -> (lhs <= rhs ? 1 : 0);
                            case LES -> (lhs < rhs ? 1 : 0);
                            case NEQ -> (lhs != rhs ? 1 : 0);
                            case LOR -> (lhs != 0 || rhs != 0 ? 1 : 0);
                            case LAND -> (lhs != 0 && rhs != 0 ? 1 : 0);
                        };
                        val.put(((TacInstr.Binary) loc.instr).dst, new Constant(res));
                        loc.instr = new TacInstr.LoadImm4(((TacInstr.Binary) loc.instr).dst,
                                res);
                        changed = true;
                    } else {
                        val.put(((TacInstr.Binary) loc.instr).dst, Constant.NAC);
                    }
                }
            } else if (loc.instr instanceof TacInstr.LoadImm4) {
                val.put(((TacInstr.LoadImm4) loc.instr).dst, new Constant(((TacInstr.LoadImm4) loc.instr).value));
            } else if (loc.instr instanceof TacInstr.CondBranch) {
                if (val.containsKey(((TacInstr.CondBranch) loc.instr).cond)) {
                    if (val.get(((TacInstr.CondBranch) loc.instr).cond).isVAL()) {
                        int cond = val.get(((TacInstr.CondBranch) loc.instr).cond).val;
                        boolean res = switch (((TacInstr.CondBranch) loc.instr).op) {
                            case BEQZ -> cond == 0;
                            case BNEZ -> cond != 0;
                        };
                        if (res) {
                            loc.instr = new TacInstr.Branch(((TacInstr.CondBranch) loc.instr).target);
                        } else {
                            loc.instr = new TacInstr.Memo("This instruction has been deleted");
                        }
                    }
                }
            } else {
                var written = loc.instr.getWritten();
                for (var temp : written) {
                    val.put(temp, Constant.NAC);
                }
            }
            loc.valOut = new TreeMap<>(val);
        }
        bb.valOut = new TreeMap<>(val);
        return changed;
    }

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

    public static class Constant {
        public enum Kind {
            VAL, NAC
        }

        public Kind kind;

        int val;

        Constant(int val) {
            this.kind = Kind.VAL;
            this.val = val;
        }

        Constant(String kind) {
            if (kind.equals("NAC")) {
                this.kind = Kind.NAC;
            }
        }

        public boolean isVAL() {
            return kind == Kind.VAL;
        }

        public boolean isNAC() {
            return kind == Kind.NAC;
        }

        public static Constant NAC = new Constant("NAC");

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Constant constant = (Constant) o;
            if (kind != constant.kind)
                return false;
            return kind != Kind.VAL || val == constant.val;
        }

        @Override
        public String toString() {
            if (kind == Kind.NAC) {
                return "NAC";
            } else {
                return "VAL: " + val;
            }
        }
    }
}