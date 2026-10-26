/*
 * StmtList.java       
 *
 * Its sibling class, `EmptyStmtList`, represents an empty `StmtList`.
 *
 * See t9.vc -- t11.vc in the spec of Assignment 3.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class StmtList extends ASTList<Stmt> {

  public StmtList(Stmt head, ASTList<Stmt> next, SourcePosition position) {
    super(head, next, position);
  }

  public boolean isEmpty() { return false; }

  public Object visit(Visitor v, Object o) {
    return v.visitStmtList(this, o);
  }

}
