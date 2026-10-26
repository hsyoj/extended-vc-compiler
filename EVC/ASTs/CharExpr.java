package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class CharExpr extends Expr {

    public CharLiteral CL;

    public CharExpr(CharLiteral clAST, SourcePosition position) {
        super(position);
        CL = clAST;
        CL.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitCharExpr(this, o);
    }
}