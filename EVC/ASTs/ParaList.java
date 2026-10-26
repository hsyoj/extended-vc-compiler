/*
 * ParaList.java       
 *
 * Used for representing a non-empty list of parameters in a function declaration.
 *
 * Its sibling class, `EmptyParaList`, represents an empty `ParaList`.
 *
 * See t13.vc and its AST given in the spec of Assignment 3.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ParaList extends ASTList<Decl> {

  public ParaList(Decl head, ASTList<Decl> next, SourcePosition position) {
    super(head, next, position);
  }

  public boolean isEmpty() { return false; }

  public Object visit(Visitor v, Object o) {
    return v.visitParaList(this, o);
  }

}
