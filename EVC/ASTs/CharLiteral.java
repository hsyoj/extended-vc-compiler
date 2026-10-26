package EVC.ASTs;

import EVC.Scanner.SourcePosition;

public class CharLiteral extends Terminal {

    public CharLiteral(String spelling, SourcePosition position) {
        super(spelling, position);
    }

    public Object visit(Visitor v, Object o) {
        return v.visitCharLiteral(this, o);
    }
}