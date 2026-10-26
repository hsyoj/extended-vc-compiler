/*
 * ErrorType.java   
 *
 * Used for representing the type of an ill-typed expression
 * in Assignment 4.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class ErrorType extends Type {

  public ErrorType(SourcePosition thePosition) {
    super (thePosition);
  }

  public Object visit (Visitor v, Object o) {
    return v.visitErrorType(this, o);
  }

  public boolean equals (Object obj) {
    return true;
  }
  public boolean assignable (Object obj) {
    return true;
  }

  public Type cloneType() {
    return new ErrorType(position);
  }

  public String toString() {
    return "error";
  }
}
