/*
 * Program.java
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class Program extends AST {

  public ASTList<Decl> FL;

  public Program (ASTList<Decl> dlAST, SourcePosition position) {
    super (position);
    FL = dlAST;
    FL.parent = this;
  }

  public Object visit(Visitor v, Object o) {
    return v.visitProgram(this, o);
  }

}
