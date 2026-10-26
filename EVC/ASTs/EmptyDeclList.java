/*
 * EmptyDeclList.java      
 *
 * Used for representing an empty DeclList.
 *
 * See t43.vc and t44.vc and their ASTs given in the Assignment 3 spec.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyDeclList extends ASTList<Decl> {
  public static final EmptyDeclList INSTANCE = new EmptyDeclList(new SourcePosition());

  public EmptyDeclList(SourcePosition position) {
    super (null, null, position);
  }

  public Decl getHead() {
    throw new UnsupportedOperationException("EmptyDeclList has no head");
  }

  public EmptyDeclList getNext() {
    throw new UnsupportedOperationException("EmptyDeclList has no next");
  }

  public boolean isEmpty() { return true; }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyDeclList(this, o);
  }

}
