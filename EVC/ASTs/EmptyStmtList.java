/*
 * EmptyStmtList.java      
 *
 * See t9.vc and t10.vc in the Assignment 3 spec.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyStmtList extends ASTList<Stmt> {
  public static final EmptyStmtList INSTANCE = new EmptyStmtList(new SourcePosition());

  public EmptyStmtList(SourcePosition position) {
    super (null, null, position);
  }

  public Stmt getHead() {
    throw new UnsupportedOperationException("EmptyStmtList has no head");
  }

  public EmptyStmtList getNext() {
    throw new UnsupportedOperationException("EmptyStmtList has no next");
  }

  public boolean isEmpty() { return true; }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyStmtList(this, o);
  }

}
