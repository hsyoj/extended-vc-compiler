/*
 * ArrayExprList.java       
 *
 * * Used for representing the list of expressions defined
 * by the following productions in the VC grammar:
 *
 * initialiser         -> expr
 *                      |  "{" expr ( "," expr )* "}"
 *
 * Given the following array declaration:
 *
 * int a[] = {3, 4, 5};
 *
 * An ArrayExprList will be created to represent the list of three expressions,
 * 3, 4 and 5, given inside { and }.
 *
 * Its sibling class, `EmptyArrayExprList`, represents an empty `ArrayExprList`.
 *
 *
 * See t43.vc and t44.vc and their ASTs given in the Assignment 3 spec.
 */


package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ArrayExprList extends ASTList<Expr> {

  // array index where this element should go
  public int index;


  public ArrayExprList(Expr head, ASTList<Expr> next, SourcePosition position) {
    super(head, next, position);
  }

  public boolean isEmpty() { return false; }

  public Object visit(Visitor v, Object o) {
    return v.visitArrayExprList(this, o);
  }

}
