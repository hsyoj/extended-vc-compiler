/*
 * Expr.java       
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public abstract class Expr extends AST {

  public Type type;
  // Filled by Checker when a context requires an implicit runtime conversion.
  // Emitter can later inspect this field to emit i2f / int->boolean / array decay, etc.
  public Type coercionType;

  public Expr (SourcePosition Position) {
    super (Position);
    type = null;
    coercionType = null;
  }

  public String toString() {
    return "Expr: " + type;
  } 

  // The following method will be used in Assignments 4 and 5.

  public boolean isEmptyExpr() {
    return this instanceof EmptyExpr;
  }

}
