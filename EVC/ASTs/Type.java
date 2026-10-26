/*
 * Type.java                      
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

import java.util.Optional;


public abstract class Type extends AST {

  public Type(SourcePosition Position) {
    super (Position);
  }

  // The following methods will be used in Assignments 4 and 5.

  // if obj and "this" are of the same type
  public abstract boolean equals(Object obj);

  //  In v = e, let "this" be the type of v and obj be the type of e. 
  //  returns true if obj is assignment compatible with "this" and
  //  false otherwise.
  public abstract boolean assignable(Object obj);

  public boolean isVoidType() {
    return (this instanceof VoidType);
  }

  public boolean isIntType() {
    return (this instanceof IntType);
  }

  public boolean isFloatType() {
    return (this instanceof FloatType);
  }

  public boolean isStringType() {
    return (this instanceof StringType);
  }

  public boolean isBooleanType() {
    return (this instanceof BooleanType);
  }

  public boolean isArrayType() {
    return (this instanceof ArrayType);
  }

  public boolean isPrimitiveType() {
    return isVoidType() || isIntType() || isFloatType() || isBooleanType() || this instanceof CharType; 
  }

  public boolean isErrorType() {
    return (this instanceof ErrorType);
  }

  // Can be used for creating the AST for multiple-variable declarations
  // See  t36.vc, t37.vc, t38.vc, and t39.vc in the spec for Assignment 3
  public abstract Type cloneType();

  public Optional<Type> getElementType() {
        return Optional.empty();
    }
}
