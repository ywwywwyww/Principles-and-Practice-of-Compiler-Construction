package decaf.backend.opt;

import decaf.backend.dataflow.*;
import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.lowlevel.instr.Temp;
import decaf.lowlevel.label.Label;
import decaf.lowlevel.tac.*;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * TAC optimization phase: optimize a TAC program.
 * <p>
 * The original decaf compiler has NO optimization, thus, we implement the transformation as identity function.
 */
public class Optimizer extends Phase<TacProg, TacProg> {
    public Optimizer(Config config) {
        super("optimizer", config);
    }

    @Override
    public TacProg transform(TacProg input) {
        for (var func : input.funcs) {
//            func.instrSeq = new ArrayList<>();
//            func.instrSeq.add(new TacInstr.Mark(func.entry));
//            func.instrSeq.add(new TacInstr.Assign(new Temp(2), new Temp(1)));
//            func.instrSeq.add(new TacInstr.Mark(new Label("L1")));
//            func.instrSeq.add(new TacInstr.Parm(new Temp(2)));
//            func.instrSeq.add(new TacInstr.DirectCall(Intrinsic.PRINT_INT));
//            func.instrSeq.add(new TacInstr.Assign(new Temp(2), new Temp(1)));
//            func.instrSeq.add(new TacInstr.CondBranch(TacInstr.CondBranch.Op.BEQZ, new Temp(2), new Label("L1")));

            // Build CFG
            var CFG = (new CFGBuilder<TacInstr>()).buildFrom(func.instrSeq, func.numArgs, func.getUsedTempCount());

            boolean deadCodeElimination = true;
            boolean copyPropagation = true;
            boolean constantPropagation = true;

            // Optimize
            boolean success = true;
            while(success) {
                success = false;

                if (deadCodeElimination) {
//                System.err.println("dead code elimination");
                    while ((new DeadCodeEliminator()).optimize(CFG)) {
                        success = true;
                    }
                }

                if (copyPropagation) {
//                System.err.println("copy propagation");
                    while ((new CopyPropagator()).optimize(CFG)) {
                        success = true;
                    }
                }

                if (constantPropagation) {
//                System.err.println("constant propagation");
                    while ((new ConstantPropagator()).optimize(CFG)) {
                        success = true;
                        func.instrSeq = output(CFG);
                        CFG = (new CFGBuilder<TacInstr>()).buildFrom(func.instrSeq, func.numArgs, func.getUsedTempCount());
                    }
                }
            }

            (new LivenessAnalyzer<TacInstr>()).accept(CFG);

            // Output instr
            func.instrSeq = output(CFG);
        }

        return input;
    }

    List<TacInstr> output(CFG<TacInstr> CFG) {
        List<TacInstr> instrSeq = new ArrayList<>();
        instrSeq.add(new TacInstr.Mark(CFG.funcLabel.get()));
        for (var node : CFG.nodes) {
            node.label.ifPresent(label -> instrSeq.add(new TacInstr.Mark(label)));
            for (var loc : node.locs) {
//                    instrSeq.add(new TacInstr.Memo(""));
//                    instrSeq.add(new TacInstr.Memo("live in : " + loc.liveIn.toString()));
                instrSeq.add(loc.instr);
//                    instrSeq.add(new TacInstr.Memo("written : " + loc.instr.getWritten().toString()));
//                    instrSeq.add(new TacInstr.Memo("live out : " + loc.liveOut.toString()));
//                    instrSeq.add(new TacInstr.Memo(""));
            }
        }
        return instrSeq;
    }

    @Override
    public void onSucceed(TacProg program) {
        if (config.target.equals(Config.Target.PA4)) {
            // First dump the tac program to file,
            var path = config.dstPath.resolve(config.getSourceBaseName() + ".tac");
            try {
                var printer = new PrintWriter(path.toFile());
                program.printTo(printer);
                printer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            // and then execute it using our simulator.
            var simulator = new Simulator(System.in, config.output);
            simulator.execute(program);
        }
    }
}
