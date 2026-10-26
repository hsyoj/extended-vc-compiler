package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class PostfixExpr extends Expr {

    public Expr E;
    public Operator O;

    public PostfixExpr(Expr eAST, Operator oAST, SourcePosition position) {
        super(position);
        E = eAST;
        O = oAST;
        E.parent = O.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitPostfixExpr(this, o);
    }
}