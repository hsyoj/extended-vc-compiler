/*
 * EmptyStmt.java      
 *
 * Used only inside IfStmt.java
 * 
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyStmt extends Stmt {

  public EmptyStmt(SourcePosition Position) {
    super (Position);
  }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyStmt(this, o);
  }

}
