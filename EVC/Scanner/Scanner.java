package EVC.Scanner;

import EVC.ErrorReporter;

public final class Scanner {
    private SourceFile sourceFile;
    private ErrorReporter errorReporter;
    private boolean debug;

    private StringBuilder currentSpelling;
    private char currentChar;
    private SourcePosition sourcePos;

    private int lineNumber;
    private int columnNumber;

    private int previousLineNumber;
    private int previousColumnNumber;

    public Scanner(SourceFile sourceFile, ErrorReporter errorReporter) {
        this.sourceFile = sourceFile;
        this.errorReporter = errorReporter;
        debug = false;
        currentChar = sourceFile.getNextChar();
        lineNumber = 1;
        columnNumber = 1;
        previousLineNumber = 1;
        previousColumnNumber = 1;
    }

    public void enableDebugging() {
        debug = true;
    }

    private void accept(int n) {
        for (int i = 0; i < n; i++) {
            accept();
        }
    }

    private void accept() {
        previousColumnNumber = columnNumber;
        previousLineNumber = lineNumber;

        if (currentChar == '\n') {
            lineNumber++;
            columnNumber = 1;
        } else if (currentChar == '\t') {
            columnNumber += 8 - ((columnNumber - 1) % 8);
        } else {
            columnNumber++;
        }

        currentChar = sourceFile.getNextChar();
    }

    private void skipSpaceAndComments() {
        while (currentChar != SourceFile.eof) {
            if (Character.isWhitespace(currentChar)) {
                accept();
                continue;
            }
            if (currentChar == '/' && inspectChar(1) == '/') {
                accept(2);
                while (currentChar != '\n' && currentChar != SourceFile.eof) {
                    accept();
                }
                continue;
            }
            if (currentChar == '/' && inspectChar(1) == '*') {
                boolean closed = false;
                SourcePosition sp = new SourcePosition(lineNumber, lineNumber, columnNumber, columnNumber);
                accept(2);
                while (currentChar != SourceFile.eof) {
                    if (currentChar == '*' && inspectChar(1) == '/') {
                        accept(2);
                        closed = true;
                        break;
                    }
                    accept();
                }
                if (!closed) {
                    errorReporter.reportError("", "unterminated comment", sp);
                }
                continue;
            }
            break;
        }
    }

    private char inspectChar(int nthChar) {
        return sourceFile.inspectChar(nthChar);
    }

    private void appendRawEscape(char escapedChar) {
        currentSpelling.append('\\');
        currentSpelling.append(escapedChar);
    }

    private void reportIllegalEscape(char escapedChar) {
        sourcePos.lineFinish = previousLineNumber;
        sourcePos.charFinish = previousColumnNumber;
        errorReporter.reportError("\\" + escapedChar, "illegal escape character", sourcePos);
    }

    private void scanEscapeSequence() {
        accept();

        switch (currentChar) {
            case '"':
            case '\\':
            case '\'':
            case 'b':
            case 'f':
            case 'n':
            case 'r':
            case 't':
                appendRawEscape(currentChar);
                accept();
                break;
            case SourceFile.eof:
                appendRawEscape('\0');
                break;
            default:
                appendRawEscape(currentChar);
                reportIllegalEscape(currentChar);
                accept();
                break;
        }
    }

    private int scanCharLiteral() {
        int logicalCharCount = 0;
        accept();

        while (currentChar != '\'' && currentChar != SourceFile.eof) {
            if (currentChar == '\n') {
                errorReporter.reportError(currentSpelling.toString(), "unterminated char literal", sourcePos);
                return Token.CHARLITERAL;
            }

            if (currentChar == '\\') {
                logicalCharCount++;
                scanEscapeSequence();
            } else {
                currentSpelling.append(currentChar);
                accept();
                logicalCharCount++;
            }
        }

        if (currentChar == '\'') {
            accept();
        } else {
            errorReporter.reportError(currentSpelling.toString(), "unterminated char literal", sourcePos);
            return Token.CHARLITERAL;
        }

        if (logicalCharCount != 1) {
            errorReporter.reportError(currentSpelling.toString(), "invalid char literal", sourcePos);
        }

        return Token.CHARLITERAL;
    }

    private int scanStringLiteral() {
        accept();

        while (currentChar != '"' && currentChar != SourceFile.eof) {
            if (currentChar == '\n') {
                errorReporter.reportError(currentSpelling.toString(), "unterminated string", sourcePos);
                return Token.STRINGLITERAL;
            }

            if (currentChar == '\\') {
                scanEscapeSequence();
            } else {
                currentSpelling.append(currentChar);
                accept();
            }
        }

        if (currentChar == '"') {
            accept();
        } else {
            errorReporter.reportError(currentSpelling.toString(), "unterminated string", sourcePos);
        }

        return Token.STRINGLITERAL;
    }

    private int nextToken() {
        switch (currentChar) {
            case '+':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '+') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.PLUSPLUS;
                }
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.PLUSEQ;
                }
                return Token.PLUS;

            case '-':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '>') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.ARROW;
                } else if (currentChar == '-') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.MINUSMINUS;
                } else if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.MINUSEQ;
                }
                return Token.MINUS;
            case '*':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.STAREQ;
                }
                return Token.MULT;
            case '/':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.DIVEQ;
                }
                return Token.DIV;
            case '%':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.MODEQ;
                }
                return Token.MOD;
            case '=':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.EQEQ;
                }
                return Token.EQ;
            case '!':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.NOTEQ;
                }
                return Token.NOT;
            case '<':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.LTEQ;
                }
                if (currentChar == '<') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.SHL;
                }
                return Token.LT;
            case '>':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '=') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.GTEQ;
                }
                if (currentChar == '>') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.SHR;
                }
                return Token.GT;
            case '&':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '&') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.ANDAND;
                }
                return Token.AMPERSAND;
            case '|':
                currentSpelling.append(currentChar);
                accept();
                if (currentChar == '|') {
                    currentSpelling.append(currentChar);
                    accept();
                    return Token.OROR;
                }
                return Token.BITOR;
            case '^':
                currentSpelling.append(currentChar);
                accept();
                return Token.BITXOR;
            case '~':
                currentSpelling.append(currentChar);
                accept();
                return Token.TILDE;
            case '(':
                currentSpelling.append(currentChar);
                accept();
                return Token.LPAREN;
            case ')':
                currentSpelling.append(currentChar);
                accept();
                return Token.RPAREN;
            case '{':
                currentSpelling.append(currentChar);
                accept();
                return Token.LCURLY;
            case '}':
                currentSpelling.append(currentChar);
                accept();
                return Token.RCURLY;
            case '[':
                currentSpelling.append(currentChar);
                accept();
                return Token.LBRACKET;
            case ']':
                currentSpelling.append(currentChar);
                accept();
                return Token.RBRACKET;
            case ',':
                currentSpelling.append(currentChar);
                accept();
                return Token.COMMA;
            case ';':
                currentSpelling.append(currentChar);
                accept();
                return Token.SEMICOLON;
            case '.':
                currentSpelling.append(currentChar);
                accept();
                if (!Character.isDigit(currentChar)) {
                    return Token.DOT;
                }
                while (Character.isDigit(currentChar)) {
                    currentSpelling.append(currentChar);
                    accept();
                }
                if (currentChar == 'e' || currentChar == 'E') {
                    if (Character.isDigit(inspectChar(1)) ||
                            ((inspectChar(1) == '+' || inspectChar(1) == '-') && Character.isDigit(inspectChar(2)))) {
                        currentSpelling.append(currentChar);
                        accept();
                        if (currentChar == '+' || currentChar == '-') {
                            currentSpelling.append(currentChar);
                            accept();
                        }
                        while (Character.isDigit(currentChar)) {
                            currentSpelling.append(currentChar);
                            accept();
                        }
                    }
                }
                return Token.FLOATLITERAL;
            case SourceFile.eof:
                currentSpelling.append(Token.spell(Token.EOF));
                return Token.EOF;
        }

        if (currentChar == '\'') {
            return scanCharLiteral();
        }

        if (currentChar == '"') {
            return scanStringLiteral();
        }

        if (Character.isLetter(currentChar) || currentChar == '_') {
            while (Character.isLetterOrDigit(currentChar) || currentChar == '_') {
                currentSpelling.append(currentChar);
                accept();
            }
            switch (currentSpelling.toString()) {
                case "int":
                    return Token.INT;
                case "float":
                    return Token.FLOAT;
                case "char":
                    return Token.CHAR;
                case "if":
                    return Token.IF;
                case "else":
                    return Token.ELSE;
                case "while":
                    return Token.WHILE;
                case "for":
                    return Token.FOR;
                case "break":
                    return Token.BREAK;
                case "continue":
                    return Token.CONTINUE;
                case "return":
                    return Token.RETURN;
                case "void":
                    return Token.VOID;
                case "boolean":
                    return Token.BOOLEAN;
                case "struct":
                    return Token.STRUCT;
                case "nullptr":
                    return Token.NULLPTR;
                case "true":
                case "false":
                    return Token.BOOLEANLITERAL;
                default:
                    return Token.ID;
            }
        }

        if (Character.isDigit(currentChar)) {
            boolean hasDot = false;
            boolean hasExp = false;
            while (Character.isDigit(currentChar)) {
                currentSpelling.append(currentChar);
                accept();
            }
            if (currentChar == '.') {
                hasDot = true;
                currentSpelling.append(currentChar);
                accept();
                while (Character.isDigit(currentChar)) {
                    currentSpelling.append(currentChar);
                    accept();
                }
            }

            if (currentChar == 'e' || currentChar == 'E') {
                if (Character.isDigit(inspectChar(1)) ||
                        ((inspectChar(1) == '+' || inspectChar(1) == '-') && Character.isDigit(inspectChar(2)))) {
                    hasExp = true;
                    currentSpelling.append(currentChar);
                    accept();
                    if (currentChar == '+' || currentChar == '-') {
                        currentSpelling.append(currentChar);
                        accept();
                    }
                    while (Character.isDigit(currentChar)) {
                        currentSpelling.append(currentChar);
                        accept();
                    }
                }
            }
            if (hasDot || hasExp) {
                return Token.FLOATLITERAL;
            }
            return Token.INTLITERAL;
        }

        currentSpelling.append(currentChar);
        accept();
        return Token.ERROR;
    }

    public Token getToken() {
        Token token;
        int kind;

        skipSpaceAndComments();

        currentSpelling = new StringBuilder();
        sourcePos = new SourcePosition(lineNumber, lineNumber, columnNumber, columnNumber);

        kind = nextToken();

        if (kind == Token.EOF) {
            sourcePos.lineFinish = lineNumber;
            sourcePos.charFinish = columnNumber;
        } else {
            sourcePos.lineFinish = previousLineNumber;
            sourcePos.charFinish = previousColumnNumber;
        }

        token = new Token(kind, currentSpelling.toString(), sourcePos);

        if (debug) {
            System.out.println(token);
        }

        return token;
    }
}
