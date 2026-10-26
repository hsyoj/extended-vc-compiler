/*
 * LocalVarDecl.java       
 *
 * See t6.vc -- t8.vc.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class LocalVarDecl extends Decl {

  public Expr E;

  public LocalVarDecl(Type tAST, Ident iAST, Expr eAST, SourcePosition position) {
    super (position);
    T = tAST;
    I = iAST;
    E = eAST;
    T.parent = I.parent = E.parent = this;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("GlobalVarDecl(");
    sb.append(T).append(" ").append(I);
    if (E != null) {
        sb.append(" = ").append(E);
    }
    sb.append(")");
    return sb.toString();
  }

  public Object visit(Visitor v, Object o) {
    return v.visitLocalVarDecl(this, o);
  }

}
