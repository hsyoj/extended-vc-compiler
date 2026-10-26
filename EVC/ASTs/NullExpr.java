package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class NullExpr extends Expr {
  public NullExpr(SourcePosition position) {
    super(position);
  }

  public Object visit(Visitor v, Object o) {
    return v.visitNullExpr(this, o);
  }
}
