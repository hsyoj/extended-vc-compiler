package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ArrayExpr extends Expr {

    public Expr V;
    public Expr E;

    public ArrayExpr(Expr vAST, Expr eAST, SourcePosition position) {
        super(position);
        V = vAST;
        E = eAST;
        V.parent = E.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitArrayExpr(this, o);
    }
}