package decaf.backend.reg;

import decaf.backend.asm.AsmEmitter;
import decaf.backend.asm.HoleInstr;
import decaf.backend.asm.SubroutineEmitter;
import decaf.backend.asm.SubroutineInfo;
import decaf.backend.dataflow.BasicBlock;
import decaf.backend.dataflow.CFG;
import decaf.backend.dataflow.LivenessAnalyzer;
import decaf.backend.dataflow.Loc;
import decaf.lowlevel.instr.PseudoInstr;
import decaf.lowlevel.instr.Reg;
import decaf.lowlevel.instr.Temp;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import javax.swing.*;
import java.lang.reflect.Array;
import java.util.*;

public class GraphColorRegAlloc extends RegAlloc {

    public GraphColorRegAlloc(AsmEmitter emitter) {
        super(emitter);
        for (var reg : emitter.allocatableRegs) {
            reg.used = false;
        }
    }

    Map<Temp, Integer> node = new TreeMap<>();
    int numGlobalTemps = 0;

    Set<Integer> spilledTemp;

    InterferenceGraph interferenceGraph;

    private Map<Temp, Reg> bindings = new TreeMap<>();

    private void bind(Temp temp, Reg reg) {
        reg.used = true;

        bindings.put(temp, reg);
        reg.occupied = true;
        reg.temp = temp;
    }

    private void unbind(Temp temp) {
        if (bindings.containsKey(temp)) {
            bindings.get(temp).occupied = false;
            bindings.remove(temp);
        }
    }

    @Override
    public void accept(CFG<PseudoInstr> graph, SubroutineInfo info) {
        // analyze (maybe already analyzed)
        (new LivenessAnalyzer<>()).accept(graph);
        System.err.printf("\n\n\n%s\n", info.funcLabel);

        // map local temp to global temp
        // TODO: replace local temp to live range information (definition-use chain)
        for (var bb : graph) {
            for (var loc : bb) {
                for (var temp : loc.instr.getWritten()) {
                    if (!(temp instanceof Reg)) {
                        node.computeIfAbsent(temp, v -> numGlobalTemps++);
//                        System.err.printf("%s %s %s\n", bb.id, temp, node.get(temp));
                    }
                }
                for (var temp : loc.instr.getRead()) {
                    if (!(temp instanceof Reg)) {
                        node.computeIfAbsent(temp, v -> numGlobalTemps++);
//                        System.err.printf("%s %s %s\n", bb.id, temp, node.get(temp));
                    }
                }
            }
        }
//        System.err.println(numGlobalTemps);
//        System.err.println(node);

        // build interference graph
        interferenceGraph = new InterferenceGraph(numGlobalTemps);
        for (var bb : graph) {
            for (var temp1 : bb.liveIn) {
                if (!(temp1 instanceof Reg)) {
                    int id1 = node.get(temp1);
                    for (var temp2 : bb.liveIn) {
                        if (!(temp2 instanceof Reg)) {
//                                System.err.printf("%s %s %s\n", bb.id, temp1, temp2);
                            int id2 = node.get(temp2);
                            if (id1 != id2) {
                                interferenceGraph.addEdge(id1, id2);
                            }
                        }
                    }
                }
            }
            for (var loc : bb) {
                for (var temp1 : loc.liveOut) {
                    if (!(temp1 instanceof Reg)) {
                        int id1 = node.get(temp1);
                        for (var temp2 : loc.liveOut) {
                            if (!(temp2 instanceof Reg)) {
//                                System.err.printf("%s %s %s\n", bb.id, temp1, temp2);
                                int id2 = node.get(temp2);
                                if (id1 != id2) {
                                    interferenceGraph.addEdge(id1, id2);
                                }
                            }
                        }
                    }
                }
            }
        }

        // graph coloring
        int numAllocableRegs = emitter.allocatableRegs.length;
        Stack<Integer> order = new Stack<>();
        for (int i = 0; i < numGlobalTemps; i++) {
            int chosen = -1;
            boolean success = false;
            for (int j = 0; j < numGlobalTemps; j++) {
                if (!interferenceGraph.removedNodes.contains(j)) {
                    if (interferenceGraph.getDegree(j) < numAllocableRegs) {
                        success = true;
                        chosen = j;
                        break;
                    }
                }
            }
            if (!success) {
                for (int j = 0; j < numGlobalTemps; j++) {
                    if (!interferenceGraph.removedNodes.contains(j) &&
                            interferenceGraph.getDegree(j) >= numAllocableRegs) {
                        if (chosen == -1 || interferenceGraph.getDegree(j) > interferenceGraph.getDegree(chosen)) {
                            chosen = j;
                        }
                    }
                }
            }
            order.push(chosen);
            interferenceGraph.removeNode(chosen);
        }
//        System.err.println(order);
        for (int i = 0; i < numGlobalTemps; i++) {
            int temp = order.pop();
            interferenceGraph.recoverNode(temp);
            Set<Integer> candidate = new TreeSet<>();
            for (int j = 0; j < numAllocableRegs; j++) {
                candidate.add(j);
            }
//            System.err.printf("removed %s\n", interferenceGraph.removedNodes);
//            System.err.printf("neighbourhood %s %s\n", temp, interferenceGraph.getNeighbourhood(temp));
            for (var neighbour : interferenceGraph.getNeighbourhood(temp)) {
//                System.err.printf("%s %s\n", temp, neighbour);
                candidate.remove(interferenceGraph.getColor(neighbour));
            }
            if (candidate.isEmpty()) {
                // TODO: spill this temp to memory : insert spill code and recoloring
                interferenceGraph.setColor(temp, -1);
                System.err.printf("ERROR: spilled temp %s\n", temp);
            } else {
//                System.err.printf("%s %s\n", temp, candidate);
//                int color = new ArrayList<>(candidate).get(random.nextInt(candidate.size()));
                int color = new ArrayList<>(candidate).get(0);
                interferenceGraph.setColor(temp, color);
            }
        }

        System.err.printf("%s\n", interferenceGraph.colors);
//        System.err.printf("%s\n", interferenceGraph.removedNodes);
//        System.err.printf("%s\n", interferenceGraph.edges);

        // alloc register
        var subEmitter = emitter.emitSubroutine(info);
        for (var reg : emitter.allocatableRegs) {
            reg.occupied = false;
        }
        bindings.clear();
        for (int i = 0; i < info.numArg; i++) {
            var cur = new Temp(i);
            subEmitter.emitLoadFromStack(allocRegFor(cur), cur);
        }
        for (var bb : graph) {
            bb.label.ifPresent(subEmitter::emitLabel);
            localAlloc(bb, subEmitter);
        }
        for (var reg : emitter.allocatableRegs) {
            reg.occupied = false;
        }
        bindings.clear();
        subEmitter.emitEnd();
    }

    private void localAlloc(BasicBlock<PseudoInstr> bb, SubroutineEmitter subEmitter) {
        var callerNeedSave = new ArrayList<Reg>();

        for (var loc : bb.allSeq()) {
            // Handle special instructions on caller save/restore.
            System.err.printf("%s\n", loc.instr);

            if (loc.instr instanceof HoleInstr) {
                if (loc.instr.equals(HoleInstr.CallerSave)) {
                    for (var reg : emitter.callerSaveRegs) {
                        if (reg.occupied) {
                            System.err.printf("save %s %s\n", reg, reg.temp);
                            callerNeedSave.add(reg);
                            subEmitter.emitStoreToStack(reg);
                        }
                    }
                    continue;
                }

                if (loc.instr.equals(HoleInstr.CallerRestore)) {
                    for (var reg : callerNeedSave) {
                        System.err.printf("restore %s %s\n", reg, reg.temp);
                        subEmitter.emitLoadFromStack(reg, reg.temp);
                    }
                    callerNeedSave.clear();
                    continue;
                }
            }

            // For normal instructions: allocate registers for every read/written temp. Skip the already specified
            // special registers.
            allocForLoc(loc, subEmitter);
        }

        // Before we leave a basic block, we must copy values of all live variables from registers (if exist)
        // to stack, as all these registers will be reset (as unoccupied) when entering another basic block.

        // Handle the last instruction, if it is a branch/return block.
        if (!bb.isEmpty() && !bb.kind.equals(BasicBlock.Kind.CONTINUOUS)) {
            allocForLoc(bb.locs.get(bb.locs.size() - 1), subEmitter);
        }
    }

    private void allocForLoc(Loc<PseudoInstr> loc, SubroutineEmitter subEmitter) {
        var instr = loc.instr;
        var srcRegs = new Reg[instr.srcs.length];
        var dstRegs = new Reg[instr.dsts.length];

        for (var i = 0; i < instr.srcs.length; i++) {
            var temp = instr.srcs[i];
            if (temp instanceof Reg) {
                srcRegs[i] = (Reg) temp;
            } else {
                srcRegs[i] = allocRegFor(temp);
            }
        }

        for (var i = 0; i < instr.dsts.length; i++) {
            var temp = instr.dsts[i];
            if (temp instanceof Reg) {
                dstRegs[i] = ((Reg) temp);
            } else {
                dstRegs[i] = allocRegFor(temp);
            }
        }

        subEmitter.emitNative(instr.toNative(dstRegs, srcRegs));
    }

    private Reg allocRegFor(Temp temp) {
//        System.err.printf("%s %s %s\n", temp, node.get(temp), interferenceGraph.getColor(node.get(temp)));
        var reg = emitter.allocatableRegs[interferenceGraph.getColor(node.get(temp))];
//        if (reg.occupied) {
//            System.err.printf("%s\n", bindings.containsKey(reg.temp));
//            unbind(reg.temp);
//        }
        bind(temp, reg);
        return reg;
    }

    private Random random = new Random();
}
