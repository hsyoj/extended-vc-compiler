/*
 * EmptyParaList.java      
 *
 * Used for representing an empty list of parameters in a function declaration.
 *
 * See t12.vc and its AST (displayed as EmptyPL) given in the Assignment 3 spec.
 */


package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class EmptyParaList extends ASTList<Decl> {
  public static final EmptyParaList INSTANCE = new EmptyParaList(new SourcePosition());

  public EmptyParaList(SourcePosition position) {
    super (null, null, position);
  }

  public Decl getHead() {
    throw new UnsupportedOperationException("EmptyParaList has no head");
  }

  public EmptyParaList getNext() {
    throw new UnsupportedOperationException("EmptyParaList has no next");
  }

  public boolean isEmpty() { return true; }

  public Object visit(Visitor v, Object o) {
    return v.visitEmptyParaList(this, o);
  }

}
