/*
 * FuncDecl.java      
 *
 *
 * See t4.vc and t5.vc.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

import java.util.List;

public class FuncDecl extends Decl {

  public ASTList<Decl> PL;
  public Stmt S;

  public FuncDecl(Type tAST, Ident idAST, ASTList<Decl> fplAST, 
         Stmt cAST, SourcePosition Position) {
    super (Position);
    T = tAST;
    I = idAST;
    PL = fplAST;
    S = cAST;
    T.parent = I.parent = PL.parent = S.parent = this;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FuncDecl(");
    sb.append(T).append(" ");  // return type
    sb.append(I);              // function name

    sb.append("(");
    if (PL != null && !PL.isEmpty()) {
        List<Decl> params = PL.toList();  // uses toList() from ASTList
        for (int i = 0; i < params.size(); i++) {
            sb.append(params.get(i));
            if (i < params.size() - 1) sb.append(", ");
        }
    }
    sb.append(")");

    sb.append(")");
    return sb.toString();
  }


  public Object visit (Visitor v, Object o) {
    return v.visitFuncDecl(this, o);
  }


}
