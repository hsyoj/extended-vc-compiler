package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class StructDefDecl extends Decl {

    public StructType S;
    public ASTList<Decl> Fields;

    public StructDefDecl(StructType sAST, ASTList<Decl> fieldsAST, SourcePosition position) {
        super(position);
        S = sAST;
        Fields = fieldsAST;
        S.parent = Fields.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitStructDefDecl(this, o);
    }
}