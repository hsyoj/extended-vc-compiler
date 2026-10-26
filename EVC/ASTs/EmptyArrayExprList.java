/*
 * EmptyArrayExprList.java      
 *
 *  Used for representing an empty ArrayExprList.
 *
 * See t43.vc and t44.vc and their ASTs given in the Assignment 3 spec.
 *
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyArrayExprList extends ASTList<Expr> {
  public static final EmptyArrayExprList INSTANCE = new EmptyArrayExprList(new SourcePosition());

  public EmptyArrayExprList(SourcePosition position) {
    super (null, null, position);
  }

  public Expr getHead() {
    throw new UnsupportedOperationException("EmptyArrayExprList has no head");
  }

  public EmptyArrayExprList getNext() {
    throw new UnsupportedOperationException("EmptyArrayExprList has no next");
  }

  public boolean isEmpty() { return true; }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyArrayExprList(this, o);
  }

}
