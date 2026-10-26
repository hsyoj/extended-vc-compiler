/*
 * ContinueStmt.java    
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ContinueStmt extends Stmt {

  public ContinueStmt(SourcePosition Position) {
    super (Position);
  }

  public Object visit(Visitor v, Object o) {
    return v.visitContinueStmt(this, o);
  }

}
