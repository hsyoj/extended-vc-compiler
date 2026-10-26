/*
 * BooleanType.java               
 *
 *
 * See, for example, t3.vc (displayed as "bool").
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class BooleanType extends Type {

  public BooleanType (SourcePosition Position) {
    super (Position);
  }

  public Object visit (Visitor v, Object o) {
    return v.visitBooleanType(this, o);
  }

  public boolean equals(Object obj) {
    if (obj != null && obj instanceof ErrorType)
      return true;
    else    
      return (obj != null && obj instanceof BooleanType);
  }

  public boolean assignable(Object obj) {
    return equals(obj);
  }

  public Type cloneType() {
    return new BooleanType(position);
  }

  public String toString() {
    return "boolean";
  }

}
