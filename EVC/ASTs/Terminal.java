/*
 * Terminal.java    
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

abstract public class Terminal extends AST {

  public String spelling;

  public Terminal (String value, SourcePosition Position) {
    super (Position);
    spelling = value;
  }

}
