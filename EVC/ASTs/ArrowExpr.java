package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ArrowExpr extends Expr {

    public Expr PtrExpr;
    public Ident Field;

    public ArrowExpr(Expr ptrExpr, Ident field, SourcePosition position) {
        super(position);
        PtrExpr = ptrExpr;
        Field = field;
        PtrExpr.parent = Field.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitArrowExpr(this, o);
    }
}