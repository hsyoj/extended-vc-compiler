/*
 * StringType.java               
 *
 * Used in Assignment 4 for performing type checking.
 *
 * No variables can be declared to have a string type, but
 * a string will be deduced to have a string type in Assignment 4.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class StringType extends Type {

  public StringType (SourcePosition Position) {
    super (Position);
  }

  public Object visit (Visitor v, Object o) {
    return v.visitStringType(this, o);
  }

  public boolean equals(Object obj) {
    if (obj instanceof ErrorType)
      return true;
    else    
      return (obj instanceof StringType);
  }

  // not used this year
  public boolean assignable(Object obj) {
    if (obj instanceof ErrorType)
      return true;
    else    
      return (obj instanceof StringType);
  }

  public Type cloneType() {
    return new StringType(position);
  }

  public String toString() {
    return "string";
  }

}
