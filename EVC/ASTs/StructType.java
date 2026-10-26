package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class StructType extends Type {

    public Ident I;

    public StructType(Ident idAST, SourcePosition position) {
        super(position);
        I = idAST;
        I.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitStructType(this, o);
    }

    public boolean equals(Object obj) {
        if (obj instanceof ErrorType) return true;
        if (obj instanceof StructType) {
            return I.spelling.equals(((StructType) obj).I.spelling);
        }
        return false;
    }

    public boolean assignable(Object obj) {
        return this.equals(obj);
    }

    @Override
    public Type cloneType() {
        return new StructType(this.I, this.position);
    }

    public String toString() {
        return "struct " + I.spelling;
    }
}