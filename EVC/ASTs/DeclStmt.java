package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class DeclStmt extends Stmt {

    public ASTList<Decl> D;

    public DeclStmt(ASTList<Decl> dAST, SourcePosition position) {
        super(position);
        D = dAST;
        D.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitDeclStmt(this, o);
    }
}