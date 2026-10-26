/*
 * BreakStmt.java    
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class BreakStmt extends Stmt {

  public BreakStmt(SourcePosition Position) {
    super (Position);
  }

  public Object visit(Visitor v, Object o) {
    return v.visitBreakStmt(this, o);
  }

}
