/*
 * CallExpr.java       
 *
 * Used for representing a call expression.
 *
 * See t24c.vc and its corresponding AST given in the spec of Assigment 3.
 */

package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class CallExpr extends Expr {

    public Expr E;
    public ASTList<Arg> AL;

    public CallExpr(Expr eAST, ASTList<Arg> alAST, SourcePosition position) {
        super(position);
        E = eAST;
        AL = alAST;
        E.parent = AL.parent = this;
    }

    public Object visit(Visitor v, Object o) {
        return v.visitCallExpr(this, o);
    }
}
