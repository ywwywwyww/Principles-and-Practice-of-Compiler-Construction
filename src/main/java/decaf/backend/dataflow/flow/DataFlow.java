package decaf.backend.dataflow.flow;

import decaf.frontend.parsing.LLParser;

public class DataFlow {
    public Copy copy = new Copy();
    public Live live = new Live();
    public Val val = new Val();
}
