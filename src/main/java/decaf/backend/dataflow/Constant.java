package decaf.backend.dataflow;

public class Constant {
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