package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class PointerType extends Type {

    public Type T;

    public PointerType(Type tAST, SourcePosition position) {
        super(position);
        T = tAST;
        T.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitPointerType(this, o);
    }

    public boolean equals(Object obj) {
        if (obj instanceof ErrorType) return true;
        if (obj instanceof PointerType) {
            return T.equals(((PointerType) obj).T);
        }
        return false;
    }

    public boolean assignable(Object obj) {
        return this.equals(obj);
    }

    @Override
    public Type cloneType() {
        return new PointerType(this.T, this.position);
    }

    public String toString() {
        return T.toString() + "*";
    }
}
