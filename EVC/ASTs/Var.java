/*
 * Var.java                   
 *
 * This is an abstract class. Currently, Var has just one subclass, SimpleVar, but we may add more in future.
 *
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public abstract class Var extends AST {

  public Type type;

  public Var (SourcePosition Position) {
    super (Position);
    type = null;
  }

}
