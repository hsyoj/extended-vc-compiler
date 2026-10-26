/*
 * VoidType.java               
 *
 * According to the VC grammar, "void x" will be considered
 * to be syntactically legal by our parser (Assignments 2 and 3)
 * but will be ruled out as being semantically illegal by our 
 * type checker (Assignment 4).
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class VoidType extends Type {

  public VoidType (SourcePosition Position) {
    super (Position);
  }

  public Object visit (Visitor v, Object o) {
    return v.visitVoidType(this, o);
  }

  public boolean equals(Object obj) {
    if (obj != null && obj instanceof ErrorType)
      return true;
    else    
      return (obj != null && obj instanceof VoidType);
  }

  // not used this year
  public boolean assignable(Object obj) {
    return equals(obj);
  }

  public Type cloneType() {
    return new VoidType(position);
  }

  public String toString() {
    return "void";
  }

}
