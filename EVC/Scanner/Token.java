package EVC.Scanner;

public final class Token {

  public int kind;
  public String spelling;
  public SourcePosition position;

  public Token(int kind, String spelling, SourcePosition position) {
    if (kind == Token.ID) {
      this.kind = determineKeywordKind(spelling);
    } else {
      this.kind = kind;
    }
    this.spelling = spelling;
    this.position = position;
  }

  private int determineKeywordKind(String spelling) {
    for (int i = firstReservedWord; i <= lastReservedWord; i++) {
      if (keywords[i].equals(spelling)) {
        return i;
      }
    }
    return Token.ID;
  }

  public static String spell(int kind) {
    return keywords[kind];
  }

  @Override
  public String toString() {
    return String.format("Kind = %d [%s], spelling = \"%s\", position = %s",
                          kind, spell(kind), spelling, position);
  }

  public static final int
    BOOLEAN     = 0,
    BREAK       = 1,
    CHAR        = 2,
    CONTINUE    = 3,
    ELSE        = 4,
    FLOAT       = 5,
    FOR         = 6,
    IF          = 7,
    INT         = 8,
    RETURN      = 9,
    STRUCT      = 10,
    VOID        = 11,
    WHILE       = 12,
    NULLPTR     = 13,
    NEW         = 14,

    PLUS        = 15,
    MINUS       = 16,
    PLUSPLUS    = 17,
    MINUSMINUS  = 18,
    PLUSEQ      = 19,
    MINUSEQ     = 20,
    MULT        = 21,
    DIVEQ       = 22,
    STAREQ      = 23,
    DIV         = 24,
    MOD         = 25,
    MODEQ       = 26,
    NOT         = 27,
    NOTEQ       = 28,
    EQ          = 29,
    EQEQ        = 30,
    LT          = 31,
    LTEQ        = 32,
    GT          = 33,
    GTEQ        = 34,
    SHL         = 35,
    SHR         = 36,
    ANDAND      = 37,
    OROR        = 38,
    AMPERSAND   = 39,
    BITOR       = 40,
    BITXOR      = 41,
    TILDE       = 42,
    DOT         = 43,
    ARROW       = 44,

    LCURLY      = 45,
    RCURLY      = 46,
    LPAREN      = 47,
    RPAREN      = 48,
    LBRACKET    = 49,
    RBRACKET    = 50,
    SEMICOLON   = 51,
    COMMA       = 52,

    ID          = 53,

    INTLITERAL  = 54,
    FLOATLITERAL= 55,
    BOOLEANLITERAL = 56,
    STRINGLITERAL = 57,
    CHARLITERAL = 58,

    ERROR       = 59,
    EOF         = 60;

  private static final String[] keywords = new String[] {
    "boolean", "break", "char", "continue", "else", "float", "for", "if", "int", "return",
    "struct", "void", "while", "nullptr", "new",
    "+", "-", "++", "--", "+=", "-=", "*", "/=", "*=", "/", "%", "%=", "!", "!=", "=", "==",
    "<", "<=", ">", ">=", "<<", ">>", "&&", "||", "&", "|", "^", "~", ".", "->",
    "{", "}", "(", ")", "[", "]", ";", ",",
    "<id>", "<int-literal>", "<float-literal>", "<boolean-literal>", "<string-literal>", "<char-literal>",
    "<error>", "$"
  };

  private static final int firstReservedWord = Token.BOOLEAN;
  private static final int lastReservedWord = Token.NEW;
}
