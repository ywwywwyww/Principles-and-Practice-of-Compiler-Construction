package decaf.printing;

import decaf.backend.dataflow.BasicBlock;
import decaf.backend.dataflow.CFG;
import decaf.lowlevel.instr.PseudoInstr;
import decaf.lowlevel.log.IndentPrinter;

/**
 * Pretty print a control flow graph.
 */
public class PrettyCFG<I extends PseudoInstr> extends PrettyPrinter<CFG<I>> {
    public PrettyCFG(IndentPrinter printer) {
        super(printer);
    }

    @Override
    public void pretty(CFG<I> graph) {
        printer.println("CFG");
        printer.incIndent();
        for (var bb : graph) {
            pretty(bb);
        }
        printer.decIndent();
        printer.println();
    }

    private void pretty(BasicBlock<I> bb) {
        printer.format("BLOCK %d", bb.id);
        bb.label.ifPresent(label -> printer.format(" (%s)", label.prettyString()));
        printer.println();

        printer.incIndent();

        printer.prettyFormatLn("def     = %s", bb.dataFlow.live.def);
        printer.prettyFormatLn("liveUse = %s", bb.dataFlow.live.use);
        printer.prettyFormatLn("liveIn  = %s", bb.dataFlow.live.in);
        printer.prettyFormatLn("liveOut = %s", bb.dataFlow.live.out);
        printer.println();

        if (bb.isEmpty()) {
            printer.println("<empty>");
        } else {
            for (var loc : bb) {
                printer.prettyFormatLn("%s # liveOut = %s", loc.instr, loc.dataFlow.live.out);
            }
        }

        printer.decIndent();
    }
}
