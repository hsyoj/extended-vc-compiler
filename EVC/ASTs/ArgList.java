/*
 * ArgList.java       
 *
 * Used for representing a non-empty list of arguments in a call.
 *
 * Its sibling class, `EmptyArgList`, represents an empty `ArgList`.
 *
 * See t15.vc and its AST given in the Assignment 3 specification.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ArgList extends ASTList<Arg> {

  public ArgList(Arg head, ASTList<Arg> next, SourcePosition position) {
    super(head, next, position);
  }

  public boolean isEmpty() { return false; }

  public Object visit(Visitor v, Object o) {
    return v.visitArgList(this, o);
  }

}
