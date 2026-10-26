package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class NewExpr extends Expr {
  public Ident I;

  public NewExpr(Ident iAST, SourcePosition position) {
    super(position);
    I = iAST;
    I.parent = this;
  }

  public Object visit(Visitor v, Object o) {
    return v.visitNewExpr(this, o);
  }
}
