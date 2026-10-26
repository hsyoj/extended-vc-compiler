package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class MemberExpr extends Expr {

    public Expr StructExpr;
    public Ident Field;

    public MemberExpr(Expr structExpr, Ident field, SourcePosition position) {
        super(position);
        StructExpr = structExpr;
        Field = field;
        StructExpr.parent = Field.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitMemberExpr(this, o);
    }
}