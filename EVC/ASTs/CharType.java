package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class CharType extends Type {

    public CharType(SourcePosition position) {
        super(position);
    }

    public Object visit(Visitor v, Object o) {
        return v.visitCharType(this, o);
    }

    public boolean equals(Object obj) {
        return obj instanceof CharType || obj instanceof ErrorType;
    }

    public boolean assignable(Object obj) {
        return this.equals(obj);
    }

    @Override
    public Type cloneType() {
        return new CharType(this.position);
    }

    public String toString() {
        return "char";
    }
}
