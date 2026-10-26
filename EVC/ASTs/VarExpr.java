/*
 * VarExpr.java       
 *
 *
 * See, for example, t24.vc, t24b.vc and t25.vc.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class VarExpr extends Expr {

  public Var V;

  public VarExpr (Var vAST, SourcePosition position) {
    super (position);
    V = vAST;
    V.parent = this;
  }

  public Object visit(Visitor v, Object o) {
    return v.visitVarExpr(this, o);
  }

}
