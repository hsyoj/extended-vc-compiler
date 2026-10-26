package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class CompoundStmt extends Stmt {

    public ASTList<Stmt> SL;

    public CompoundStmt(ASTList<Stmt> slAST, SourcePosition position) {
        super(position);
        SL = slAST;
        SL.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitCompoundStmt(this, o);
    }
}