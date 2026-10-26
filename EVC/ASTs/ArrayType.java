package EVC.ASTs;

import EVC.Scanner.SourcePosition;
import java.util.Optional;

public class ArrayType extends Type {

    public Type T;
    public Expr E;

    public ArrayType (Type tAST, Expr dAST, SourcePosition Position) {
        super(Position);
        T = tAST;
        E = dAST;
        T.parent = E.parent = this;
    }

    public Object visit (Visitor v, Object o) {
        return v.visitArrayType(this, o);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ErrorType) return true;

        if (obj instanceof ArrayType) {
            return this.T.equals(((ArrayType) obj).T);
        }

        if (obj instanceof PointerType) {
            return this.T.equals(((PointerType) obj).T);
        }

        return false;
    }

    @Override
    public boolean assignable(Object obj) {
        if (obj instanceof ErrorType) return true;

        if (obj instanceof ArrayType) {
            return this.T.assignable(((ArrayType) obj).T);
        }

        if (obj instanceof PointerType) {
            return this.T.assignable(((PointerType) obj).T);
        }

        return false;
    }

    public Type cloneType() {
        return new ArrayType(T.cloneType(), new EmptyExpr(position), position);
    }

    public Optional<Type> getElementType() {
        return Optional.of(T);
    }

    @Override
    public String toString() {
        if (T instanceof IntType)
            return "[I";
        else if (T instanceof FloatType)
            return "[F";
        else if (T instanceof BooleanType)
            return "[Z";
        else if (T instanceof VoidType)
            return "[V";
        else if (T instanceof CharType)
            return "[C";
        else if (T instanceof StructType)
            return "[L" + ((StructType) T).I.spelling + ";";
        else if (T instanceof PointerType)
            return "[" + T.toString();
        else if (T instanceof ArrayType)
            return "[" + T.toString();
        else
            return "[*";
    }
}