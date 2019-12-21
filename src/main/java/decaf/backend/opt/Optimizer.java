package decaf.backend.opt;

import decaf.backend.dataflow.CFG;
import decaf.backend.dataflow.CFGBuilder;
import decaf.backend.dataflow.LivenessAnalyzer;
import decaf.driver.Config;
import decaf.driver.Phase;
import decaf.lowlevel.tac.Simulator;
import decaf.lowlevel.tac.TacFunc;
import decaf.lowlevel.tac.TacInstr;
import decaf.lowlevel.tac.TacProg;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;

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

            // Build CFG
            var CFG = (new CFGBuilder<TacInstr>()).buildFrom(func.instrSeq);

            // Analyze
            (new LivenessAnalyzer<TacInstr>()).accept(CFG);

            // TODO: Optimize

            // Output instr
            func.instrSeq = new ArrayList<>();
            func.instrSeq.add(new TacInstr.Mark(CFG.funcLabel.get()));
            for (var node : CFG.nodes) {
                node.label.ifPresent(label -> func.instrSeq.add(new TacInstr.Mark(label)));
                for (var loc : node.locs) {
                    func.instrSeq.add(loc.instr);
                }
            }
        }

        return input;
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
