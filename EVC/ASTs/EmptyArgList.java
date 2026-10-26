/*
 * EmptyDeclList.java      
 *
 * Used for representing an empty list of arguments in a call.
 *
 * See t14.vc and its AST (displayed as EmptyAL) in the Assignment 3 spec.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyArgList extends ASTList<Arg> {
  public static final EmptyArgList INSTANCE = new EmptyArgList(new SourcePosition());

  public EmptyArgList(SourcePosition position) {
    super (null, null, position);
  }

  public Arg getHead() {
    throw new UnsupportedOperationException("EmptyArgList has no head");
  }

  public EmptyArgList getNext() {
    throw new UnsupportedOperationException("EmptyArgList has no next");
  }

  public boolean isEmpty() { return true; }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyArgList(this, o);
  }

}
