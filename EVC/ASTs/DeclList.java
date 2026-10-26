/*
 * DeclList.java       
 *
 * Represents a list of declarations, including function declarations,
 * global variable declarations, and local variable declarations.
 *
 * A Program consists of a DeclList containing function and global 
 * variable declarations.
 *
 * Each function's body, represented by a compound statement (or block), 
 * is split into two lists: a DeclList for its local variable declarations 
 * and a StmtList for its statements.
 *
 * Its sibling class, `EmptyDeclList`, represents an empty `DeclList`.
 *
 * Refer to t2.vc, t8.vc, and t42.vc in the Assignment 3 spec.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class DeclList extends ASTList<Decl> {

  public DeclList(Decl head, ASTList<Decl> next, SourcePosition position) {
    super(head, next, position);
  }

  public boolean isEmpty() { return false; }

  public Object visit(Visitor v, Object o) {
    return v.visitDeclList(this, o);
  }

}
