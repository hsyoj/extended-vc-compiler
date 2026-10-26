package EVC.Parser;

import EVC.Scanner.Scanner;
import EVC.Scanner.SourcePosition;
import EVC.Scanner.Token;
import EVC.ErrorReporter;

import EVC.ASTs.*;

public class Parser {

    private final Scanner scanner;
    private final ErrorReporter errorReporter;
    private Token currentToken;
    private Token nextToken;
    private SourcePosition previousTokenPosition;
    private final SourcePosition dummyPos = new SourcePosition();

    private static class ParsedDeclarator {
        final Ident id;
        final Type type;
        final SourcePosition position;

        ParsedDeclarator(Ident id, Type type, SourcePosition position) {
            this.id = id;
            this.type = type;
            this.position = position;
        }
    }

    public Parser(Scanner lexer, ErrorReporter reporter) {
        scanner = lexer;
        errorReporter = reporter;
        previousTokenPosition = new SourcePosition();
        currentToken = scanner.getToken();
        nextToken = scanner.getToken();
    }

    void match(int tokenExpected) throws SyntaxError {
        if (currentToken.kind == tokenExpected) {
            accept();
        } else {
            throw syntacticError("\"%\" expected here", Token.spell(tokenExpected));
        }
    }

    void accept() {
        previousTokenPosition = currentToken.position;
        currentToken = nextToken;
        nextToken = scanner.getToken();
    }

    private SyntaxError syntacticError(String messageTemplate, String tokenQuoted) throws SyntaxError {
        SourcePosition pos = currentToken.position;
        errorReporter.reportError(messageTemplate, tokenQuoted, pos);
        return new SyntaxError();
    }

    void start(SourcePosition position) {
        position.lineStart = currentToken.position.lineStart;
        position.charStart = currentToken.position.charStart;
    }

    void finish(SourcePosition position) {
        position.lineFinish = previousTokenPosition.lineFinish;
        position.charFinish = previousTokenPosition.charFinish;
    }

    void copyStart(SourcePosition from, SourcePosition to) {
        to.lineStart = from.lineStart;
        to.charStart = from.charStart;
    }

    boolean isType(int tokenID) {
        return tokenID == Token.INT || tokenID == Token.FLOAT ||
                tokenID == Token.BOOLEAN || tokenID == Token.VOID ||
                tokenID == Token.CHAR || tokenID == Token.STRUCT;
    }

    boolean isStartOfExpr(int tokenID) {
        return tokenID == Token.INTLITERAL || tokenID == Token.FLOATLITERAL || tokenID == Token.STRINGLITERAL ||
                tokenID == Token.CHARLITERAL || tokenID == Token.PLUS || tokenID == Token.MINUS ||
                tokenID == Token.ID || tokenID == Token.BOOLEANLITERAL || tokenID == Token.LPAREN ||
                tokenID == Token.NOT || tokenID == Token.AMPERSAND || tokenID == Token.MULT ||
                tokenID == Token.PLUSPLUS || tokenID == Token.MINUSMINUS || tokenID == Token.NULLPTR || tokenID == Token.NEW ||
                tokenID == Token.TILDE;
    }

    public Program parseProgram() {
        SourcePosition programPos = new SourcePosition();
        start(programPos);
        try {
            ASTList<Decl> dlAST = parseDeclList();
            finish(programPos);
            return new Program(dlAST, programPos);
        } catch (SyntaxError s) {
            return null;
        }
    }

    ASTList<Decl> parseDeclList() throws SyntaxError {
        if (currentToken.kind == Token.EOF) {
            return new EmptyDeclList(dummyPos);
        }

        SourcePosition pos = new SourcePosition();
        start(pos);

        Type baseType = parseBaseType(true);

        if (baseType instanceof StructType && currentToken.kind == Token.LCURLY) {
            accept();
            ASTList<Decl> fields = parseStructFieldList();
            match(Token.RCURLY);
            match(Token.SEMICOLON);
            finish(pos);
            Decl structDef = new StructDefDecl((StructType) baseType, fields, pos);
            return new DeclList(structDef, parseDeclList(), pos);
        }

        ParsedDeclarator first = parseDeclarator(baseType, pos);

        if (currentToken.kind == Token.LPAREN) {
            Decl funcDecl = parseFuncRest(first.type, first.id, pos);
            ASTList<Decl> tail = parseDeclList();
            return new DeclList(funcDecl, tail, pos);
        }

        ASTList<Decl> vars = parseGlobalVarDeclRest(baseType, first, pos);
        ASTList<Decl> tail = parseDeclList();
        return appendDeclLists(vars, tail);
    }

    ASTList<Decl> parseStructFieldList() throws SyntaxError {
        if (!isType(currentToken.kind)) {
            return new EmptyDeclList(dummyPos);
        }

        SourcePosition pos = new SourcePosition();
        start(pos);
        Type baseType = parseBaseType(false);
        ParsedDeclarator first = parseDeclarator(baseType, pos);
        ASTList<Decl> fields = parseStructFieldDeclRest(baseType, first, pos);
        match(Token.SEMICOLON);
        ASTList<Decl> tail = parseStructFieldList();
        return appendDeclLists(fields, tail);
    }

    ASTList<Decl> parseStructFieldDeclRest(Type baseType, ParsedDeclarator first, SourcePosition startPos) throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        copyStart(startPos, pos);
        finish(pos);

        Decl field = new GlobalVarDecl(first.type, first.id, new EmptyExpr(dummyPos), pos);

        if (currentToken.kind == Token.EQ) {
            throw syntacticError("invalid initialiser in struct field declaration", currentToken.spelling);
        }

        if (currentToken.kind == Token.COMMA) {
            accept();
            ParsedDeclarator next = parseDeclarator(baseType, startPos);
            ASTList<Decl> tail = parseStructFieldDeclRest(baseType, next, startPos);
            return new DeclList(field, tail, pos);
        }

        return new DeclList(field, new EmptyDeclList(dummyPos), pos);
    }

    Decl parseFuncRest(Type tAST, Ident iAST, SourcePosition pos) throws SyntaxError {
        match(Token.LPAREN);
        ASTList<Decl> params = parseParaList();
        match(Token.RPAREN);

        if (currentToken.kind == Token.SEMICOLON) {
            accept();
            finish(pos);
            return new FuncDecl(tAST, iAST, params, new EmptyStmt(dummyPos), pos);
        }

        Stmt compStmt = parseCompoundStmt();
        finish(pos);
        return new FuncDecl(tAST, iAST, params, compStmt, pos);
    }

    ASTList<Decl> parseGlobalVarDeclRest(Type baseType, ParsedDeclarator first, SourcePosition startPos) throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        copyStart(startPos, pos);
        Decl varDecl = parseOneGlobalVar(first.type, first.id, pos);

        if (currentToken.kind == Token.COMMA) {
            accept();
            ParsedDeclarator next = parseDeclarator(baseType, startPos);
            ASTList<Decl> tail = parseGlobalVarDeclRest(baseType, next, startPos);
            return new DeclList(varDecl, tail, pos);
        }

        match(Token.SEMICOLON);
        return new DeclList(varDecl, new EmptyDeclList(dummyPos), pos);
    }

    Decl parseOneGlobalVar(Type tAST, Ident iAST, SourcePosition pos) throws SyntaxError {
        if (tAST instanceof VoidType) {
            throw syntacticError("invalid variable type", iAST.spelling);
        }

        Expr initExpr = new EmptyExpr(dummyPos);
        if (currentToken.kind == Token.EQ) {
            accept();
            initExpr = parseInitialiser();
        }
        finish(pos);
        return new GlobalVarDecl(tAST, iAST, initExpr, pos);
    }

    ASTList<Decl> parseParaList() throws SyntaxError {
        if (currentToken.kind == Token.RPAREN) {
            return new EmptyParaList(dummyPos);
        }

        if (currentToken.kind == Token.VOID && nextToken.kind == Token.RPAREN) {
            accept();
            return new EmptyParaList(dummyPos);
        }

        return parseParaDeclList();
    }

    ASTList<Decl> parseParaDeclList() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);

        Type baseType = parseBaseType(false);
        ParsedDeclarator first = parseDeclarator(baseType, pos);
        Type paramType = decayParameterType(first.type);
        Decl pAST = new ParaDecl(paramType, first.id, first.position);

        ASTList<Decl> tail;
        if (currentToken.kind == Token.COMMA) {
            accept();
            tail = parseParaDeclList();
        } else {
            tail = new EmptyParaList(dummyPos);
        }

        finish(pos);
        return new ParaList(pAST, tail, pos);
    }

    Stmt parseCompoundStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        match(Token.LCURLY);

        ASTList<Stmt> stmts = parseMixedStmtList();

        match(Token.RCURLY);
        finish(pos);

        return new CompoundStmt(stmts, pos);
    }

    ASTList<Stmt> parseMixedStmtList() throws SyntaxError {
        if (currentToken.kind == Token.RCURLY) {
            return new EmptyStmtList(dummyPos);
        }

        SourcePosition pos = new SourcePosition();
        start(pos);
        Stmt sAST;

        if (isType(currentToken.kind)) {
            Type baseType = parseBaseType(false);
            ParsedDeclarator first = parseDeclarator(baseType, pos);
            ASTList<Decl> decls = parseLocalVarDeclRest(baseType, first, pos);
            sAST = new DeclStmt(decls, pos);
        } else {
            sAST = parseStmt();
        }

        ASTList<Stmt> tail = parseMixedStmtList();
        finish(pos);
        return new StmtList(sAST, tail, pos);
    }

    ASTList<Decl> parseLocalVarDeclRest(Type baseType, ParsedDeclarator first, SourcePosition startPos) throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        copyStart(startPos, pos);
        Decl varDecl = parseOneLocalVar(first.type, first.id, pos);

        if (currentToken.kind == Token.COMMA) {
            accept();
            ParsedDeclarator next = parseDeclarator(baseType, startPos);
            ASTList<Decl> tail = parseLocalVarDeclRest(baseType, next, startPos);
            return new DeclList(varDecl, tail, pos);
        }

        match(Token.SEMICOLON);
        return new DeclList(varDecl, new EmptyDeclList(dummyPos), pos);
    }

    Decl parseOneLocalVar(Type tAST, Ident iAST, SourcePosition pos) throws SyntaxError {
        if (tAST instanceof VoidType) {
            throw syntacticError("invalid variable type", iAST.spelling);
        }

        Expr initExpr = new EmptyExpr(dummyPos);
        if (currentToken.kind == Token.EQ) {
            accept();
            initExpr = parseInitialiser();
        }
        finish(pos);
        return new LocalVarDecl(tAST, iAST, initExpr, pos);
    }

    Stmt parseStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Stmt sAST;

        switch (currentToken.kind) {
            case Token.LCURLY:
                sAST = parseCompoundStmt();
                break;
            case Token.IF:
                sAST = parseIfStmt();
                break;
            case Token.WHILE:
                sAST = parseWhileStmt();
                break;
            case Token.FOR:
                sAST = parseForStmt();
                break;
            case Token.RETURN:
                sAST = parseReturnStmt();
                break;
            case Token.CONTINUE:
                match(Token.CONTINUE);
                match(Token.SEMICOLON);
                finish(pos);
                sAST = new ContinueStmt(pos);
                break;
            case Token.BREAK:
                match(Token.BREAK);
                match(Token.SEMICOLON);
                finish(pos);
                sAST = new BreakStmt(pos);
                break;
            case Token.SEMICOLON:
                accept();
                finish(pos);
                sAST = new ExprStmt(new EmptyExpr(pos), pos);
                break;
            default:
                Expr e = parseExpr();
                match(Token.SEMICOLON);
                finish(pos);
                sAST = new ExprStmt(e, pos);
                break;
        }
        return sAST;
    }

    Stmt parseIfStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        match(Token.IF);
        match(Token.LPAREN);
        Expr cond = parseExpr();
        match(Token.RPAREN);
        Stmt s1 = parseStmt();
        Stmt s2 = new EmptyStmt(dummyPos);
        if (currentToken.kind == Token.ELSE) {
            match(Token.ELSE);
            s2 = parseStmt();
        }
        finish(pos);
        return new IfStmt(cond, s1, s2, pos);
    }

    Stmt parseWhileStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        match(Token.WHILE);
        match(Token.LPAREN);
        Expr cond = parseExpr();
        match(Token.RPAREN);
        Stmt s = parseStmt();
        finish(pos);
        return new WhileStmt(cond, s, pos);
    }

    Stmt parseForStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        match(Token.FOR);
        match(Token.LPAREN);

        if (isType(currentToken.kind)) {
            SourcePosition declPos = new SourcePosition();
            start(declPos);
            Type baseType = parseBaseType(false);
            ParsedDeclarator first = parseDeclarator(baseType, declPos);
            ASTList<Decl> decls = parseLocalVarDeclRest(baseType, first, declPos);
            DeclStmt initDeclStmt = new DeclStmt(decls, declPos);

            Expr e2 = new EmptyExpr(dummyPos);
            if (isStartOfExpr(currentToken.kind)) e2 = parseExpr();
            match(Token.SEMICOLON);

            Expr e3 = new EmptyExpr(dummyPos);
            if (isStartOfExpr(currentToken.kind)) e3 = parseExpr();
            match(Token.RPAREN);

            Stmt body = parseStmt();
            ForStmt forStmt = new ForStmt(new EmptyExpr(dummyPos), e2, e3, body, pos);
            ASTList<Stmt> stmtTail = new StmtList(forStmt, new EmptyStmtList(dummyPos), pos);
            ASTList<Stmt> stmtList = new StmtList(initDeclStmt, stmtTail, declPos);
            finish(pos);
            return new CompoundStmt(stmtList, pos);
        }

        Expr e1 = new EmptyExpr(dummyPos);
        if (isStartOfExpr(currentToken.kind)) e1 = parseExpr();
        match(Token.SEMICOLON);

        Expr e2 = new EmptyExpr(dummyPos);
        if (isStartOfExpr(currentToken.kind)) e2 = parseExpr();
        match(Token.SEMICOLON);

        Expr e3 = new EmptyExpr(dummyPos);
        if (isStartOfExpr(currentToken.kind)) e3 = parseExpr();
        match(Token.RPAREN);

        Stmt s = parseStmt();
        finish(pos);
        return new ForStmt(e1, e2, e3, s, pos);
    }

    Stmt parseReturnStmt() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        match(Token.RETURN);
        Expr e = new EmptyExpr(dummyPos);
        if (isStartOfExpr(currentToken.kind)) {
            e = parseExpr();
        }
        match(Token.SEMICOLON);
        finish(pos);
        return new ReturnStmt(e, pos);
    }

    Expr parseExpr() throws SyntaxError {
        return parseAssignExpr();
    }

    Expr parseAssignExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e1 = parseLogicalOrExpr();
        if (isAssignmentOperator(currentToken.kind)) {
            String op = currentToken.spelling;
            accept();
            Expr e2 = parseAssignExpr();
            finish(pos);
            if (op.equals("=")) {
                return new AssignExpr(e1, e2, pos);
            }
            String baseOp = baseAssignmentOperator(op);
            Expr lhsCopy = cloneExpr(e1);
            BinaryExpr rhs = new BinaryExpr(lhsCopy, new Operator(baseOp, clonePos(pos)), e2, clonePos(pos));
            return new AssignExpr(e1, rhs, pos);
        }
        return e1;
    }

    Expr parseLogicalOrExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseLogicalAndExpr();
        while (currentToken.kind == Token.OROR) {
            Operator op = acceptOperator();
            Expr e2 = parseLogicalAndExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseLogicalAndExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseBitwiseOrExpr();
        while (currentToken.kind == Token.ANDAND) {
            Operator op = acceptOperator();
            Expr e2 = parseBitwiseOrExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseBitwiseOrExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseBitwiseXorExpr();
        while (currentToken.kind == Token.BITOR) {
            Operator op = acceptOperator();
            Expr e2 = parseBitwiseXorExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseBitwiseXorExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseBitwiseAndExpr();
        while (currentToken.kind == Token.BITXOR) {
            Operator op = acceptOperator();
            Expr e2 = parseBitwiseAndExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseBitwiseAndExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseEqualityExpr();
        while (currentToken.kind == Token.AMPERSAND) {
            Operator op = acceptOperator();
            Expr e2 = parseEqualityExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseEqualityExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseRelationalExpr();
        while (currentToken.kind == Token.EQEQ || currentToken.kind == Token.NOTEQ) {
            Operator op = acceptOperator();
            Expr e2 = parseRelationalExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseRelationalExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseShiftExpr();
        while (currentToken.kind == Token.LT || currentToken.kind == Token.LTEQ ||
                currentToken.kind == Token.GT || currentToken.kind == Token.GTEQ) {
            Operator op = acceptOperator();
            Expr e2 = parseShiftExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }


    Expr parseShiftExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseAdditiveExpr();
        while (currentToken.kind == Token.SHL || currentToken.kind == Token.SHR) {
            Operator op = acceptOperator();
            Expr e2 = parseAdditiveExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseAdditiveExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseMultiplicativeExpr();
        while (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS) {
            Operator op = acceptOperator();
            Expr e2 = parseMultiplicativeExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseMultiplicativeExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parseUnaryExpr();
        while (currentToken.kind == Token.MULT || currentToken.kind == Token.DIV || currentToken.kind == Token.MOD) {
            Operator op = acceptOperator();
            Expr e2 = parseUnaryExpr();
            SourcePosition newPos = new SourcePosition();
            copyStart(pos, newPos);
            finish(newPos);
            e = new BinaryExpr(e, op, e2, newPos);
            pos = newPos;
        }
        return e;
    }

    Expr parseUnaryExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        if (currentToken.kind == Token.PLUS || currentToken.kind == Token.MINUS ||
                currentToken.kind == Token.NOT || currentToken.kind == Token.AMPERSAND ||
                currentToken.kind == Token.MULT || currentToken.kind == Token.PLUSPLUS ||
                currentToken.kind == Token.MINUSMINUS || currentToken.kind == Token.TILDE) {
            Operator op = acceptOperator();
            Expr e = parseUnaryExpr();
            finish(pos);
            return new UnaryExpr(op, e, pos);
        }
        return parsePostfixExpr();
    }

    Expr parsePostfixExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e = parsePrimaryExpr();

        while (true) {
            if (currentToken.kind == Token.LBRACKET) {
                accept();
                Expr index = parseExpr();
                match(Token.RBRACKET);
                SourcePosition newPos = new SourcePosition();
                copyStart(pos, newPos);
                finish(newPos);
                e = new ArrayExpr(e, index, newPos);
                pos = newPos;
            } else if (currentToken.kind == Token.LPAREN) {
                accept();
                ASTList<Arg> args = parseArgList();
                match(Token.RPAREN);
                SourcePosition newPos = new SourcePosition();
                copyStart(pos, newPos);
                finish(newPos);
                e = new CallExpr(e, args, newPos);
                pos = newPos;
            } else if (currentToken.kind == Token.DOT) {
                accept();
                Ident field = parseIdent();
                SourcePosition newPos = new SourcePosition();
                copyStart(pos, newPos);
                finish(newPos);
                e = new MemberExpr(e, field, newPos);
                pos = newPos;
            } else if (currentToken.kind == Token.ARROW) {
                accept();
                Ident field = parseIdent();
                SourcePosition newPos = new SourcePosition();
                copyStart(pos, newPos);
                finish(newPos);
                e = new ArrowExpr(e, field, newPos);
                pos = newPos;
            } else if (currentToken.kind == Token.PLUSPLUS || currentToken.kind == Token.MINUSMINUS) {
                Operator op = acceptOperator();
                SourcePosition newPos = new SourcePosition();
                copyStart(pos, newPos);
                finish(newPos);
                e = new PostfixExpr(e, op, newPos);
                pos = newPos;
            } else {
                break;
            }
        }
        return e;
    }

    Expr parsePrimaryExpr() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        Expr e;

        switch (currentToken.kind) {
            case Token.ID:
                Ident id = parseIdent();
                finish(pos);
                e = new VarExpr(new SimpleVar(id, pos), pos);
                break;
            case Token.LPAREN:
                accept();
                e = parseExpr();
                match(Token.RPAREN);
                break;
            case Token.INTLITERAL:
                IntLiteral il = parseIntLiteral();
                finish(pos);
                e = new IntExpr(il, pos);
                break;
            case Token.FLOATLITERAL:
                FloatLiteral fl = parseFloatLiteral();
                finish(pos);
                e = new FloatExpr(fl, pos);
                break;
            case Token.BOOLEANLITERAL:
                BooleanLiteral bl = parseBooleanLiteral();
                finish(pos);
                e = new BooleanExpr(bl, pos);
                break;
            case Token.CHARLITERAL:
                CharLiteral cl = parseCharLiteral();
                finish(pos);
                e = new CharExpr(cl, pos);
                break;
            case Token.STRINGLITERAL:
                StringLiteral sl = parseStringLiteral();
                finish(pos);
                e = new StringExpr(sl, pos);
                break;
            case Token.NULLPTR:
                accept();
                finish(pos);
                e = new NullExpr(pos);
                break;
            case Token.NEW:
                accept();
                Ident sid = parseIdent();
                finish(pos);
                e = new NewExpr(sid, pos);
                break;
            default:
                throw syntacticError("illegal primary expression", currentToken.spelling);
        }
        return e;
    }

    ASTList<Arg> parseArgList() throws SyntaxError {
        if (currentToken.kind == Token.RPAREN || !isStartOfExpr(currentToken.kind)) {
            return new EmptyArgList(dummyPos);
        }

        Expr e = parseExpr();
        Arg currentArg = new Arg(e, e.position);

        ASTList<Arg> tail;
        if (currentToken.kind == Token.COMMA) {
            accept();
            tail = parseArgList();
        } else {
            tail = new EmptyArgList(dummyPos);
        }
        return new ArgList(currentArg, tail, currentArg.position);
    }

    ASTList<Expr> parseArrayExprList() throws SyntaxError {
        Expr e = parseInitialiser();
        ASTList<Expr> tail;
        if (currentToken.kind == Token.COMMA) {
            accept();
            tail = parseArrayExprList();
        } else {
            tail = new EmptyArrayExprList(dummyPos);
        }
        return new ArrayExprList(e, tail, e.position);
    }

    Expr parseInitialiser() throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);
        if (currentToken.kind == Token.LCURLY) {
            accept();
            ASTList<Expr> exprList;
            if (currentToken.kind != Token.RCURLY) {
                exprList = parseArrayExprList();
            } else {
                exprList = new EmptyArrayExprList(dummyPos);
            }
            match(Token.RCURLY);
            finish(pos);
            return new ArrayInitExpr(exprList, pos);
        }
        return parseExpr();
    }

    Type parseBaseType(boolean allowVoid) throws SyntaxError {
        SourcePosition pos = new SourcePosition();
        start(pos);

        if (currentToken.kind == Token.STRUCT) {
            accept();
            Ident id = parseIdent();
            finish(pos);
            return new StructType(id, pos);
        }

        Type tAST = switch (currentToken.kind) {
            case Token.INT -> {
                accept();
                finish(pos);
                yield new IntType(pos);
            }
            case Token.FLOAT -> {
                accept();
                finish(pos);
                yield new FloatType(pos);
            }
            case Token.BOOLEAN -> {
                accept();
                finish(pos);
                yield new BooleanType(pos);
            }
            case Token.VOID -> {
                if (!allowVoid) {
                    throw syntacticError("void type is not allowed here", currentToken.spelling);
                }
                accept();
                finish(pos);
                yield new VoidType(pos);
            }
            case Token.CHAR -> {
                accept();
                finish(pos);
                yield new CharType(pos);
            }
            default -> throw syntacticError("type expected here", currentToken.spelling);
        };

        return tAST;
    }

    ParsedDeclarator parseDeclarator(Type baseType, SourcePosition startPos) throws SyntaxError {
        Type tAST = baseType;
        SourcePosition pos = new SourcePosition();
        copyStart(startPos, pos);

        while (currentToken.kind == Token.MULT) {
            accept();
            SourcePosition pointerPos = new SourcePosition();
            copyStart(startPos, pointerPos);
            finish(pointerPos);
            tAST = new PointerType(tAST, pointerPos);
        }

        Ident iAST = parseIdent();

        java.util.ArrayList<Expr> dimensions = new java.util.ArrayList<>();
        java.util.ArrayList<SourcePosition> dimPositions = new java.util.ArrayList<>();
        while (currentToken.kind == Token.LBRACKET) {
            SourcePosition arrayPos = new SourcePosition();
            copyStart(startPos, arrayPos);
            accept();
            Expr indexExpr = new EmptyExpr(dummyPos);
            if (currentToken.kind == Token.INTLITERAL) {
                IntLiteral sizeLiteral = parseIntLiteral();
                indexExpr = new IntExpr(sizeLiteral, sizeLiteral.position);
            }
            match(Token.RBRACKET);
            finish(arrayPos);
            dimensions.add(indexExpr);
            dimPositions.add(arrayPos);
        }

        for (int i = dimensions.size() - 1; i >= 0; i--) {
            tAST = new ArrayType(tAST, dimensions.get(i), dimPositions.get(i));
        }

        finish(pos);
        return new ParsedDeclarator(iAST, tAST, pos);
    }

    private boolean isAssignmentOperator(int kind) {
        return kind == Token.EQ || kind == Token.PLUSEQ || kind == Token.MINUSEQ ||
                kind == Token.STAREQ || kind == Token.DIVEQ || kind == Token.MODEQ;
    }

    private String baseAssignmentOperator(String spelling) {
        return switch (spelling) {
            case "+=" -> "+";
            case "-=" -> "-";
            case "*=" -> "*";
            case "/=" -> "/";
            case "%=" -> "%";
            default -> spelling;
        };
    }

    private Type decayParameterType(Type type) {
        if (type instanceof ArrayType at) {
            return new PointerType(at.T, clonePos(type.position));
        }
        return type;
    }

    private SourcePosition clonePos(SourcePosition p) {
        SourcePosition q = new SourcePosition();
        q.lineStart = p.lineStart;
        q.lineFinish = p.lineFinish;
        q.charStart = p.charStart;
        q.charFinish = p.charFinish;
        return q;
    }

    private Expr cloneExpr(Expr e) {
        if (e instanceof VarExpr ve && ve.V instanceof SimpleVar sv) {
            Ident id = new Ident(sv.I.spelling, clonePos(sv.I.position));
            id.decl = sv.I.decl;
            return new VarExpr(new SimpleVar(id, clonePos(ve.position)), clonePos(ve.position));
        }
        if (e instanceof ArrayExpr ae) {
            return new ArrayExpr(cloneExpr(ae.V), cloneExpr(ae.E), clonePos(ae.position));
        }
        if (e instanceof MemberExpr me) {
            Ident field = new Ident(me.Field.spelling, clonePos(me.Field.position));
            field.decl = me.Field.decl;
            return new MemberExpr(cloneExpr(me.StructExpr), field, clonePos(me.position));
        }
        if (e instanceof ArrowExpr ae) {
            Ident field = new Ident(ae.Field.spelling, clonePos(ae.Field.position));
            field.decl = ae.Field.decl;
            return new ArrowExpr(cloneExpr(ae.PtrExpr), field, clonePos(ae.position));
        }
        if (e instanceof UnaryExpr ue) {
            return new UnaryExpr(new Operator(ue.O.spelling, clonePos(ue.O.position)), cloneExpr(ue.E), clonePos(ue.position));
        }
        if (e instanceof PostfixExpr pe) {
            return new PostfixExpr(cloneExpr(pe.E), new Operator(pe.O.spelling, clonePos(pe.O.position)), clonePos(pe.position));
        }
        if (e instanceof IntExpr ie) {
            return new IntExpr(new IntLiteral(ie.IL.spelling, clonePos(ie.IL.position)), clonePos(ie.position));
        }
        if (e instanceof FloatExpr fe) {
            return new FloatExpr(new FloatLiteral(fe.FL.spelling, clonePos(fe.FL.position)), clonePos(fe.position));
        }
        if (e instanceof BooleanExpr be) {
            return new BooleanExpr(new BooleanLiteral(be.BL.spelling, clonePos(be.BL.position)), clonePos(be.position));
        }
        if (e instanceof CharExpr ce) {
            return new CharExpr(new CharLiteral(ce.CL.spelling, clonePos(ce.CL.position)), clonePos(ce.position));
        }
        if (e instanceof StringExpr se) {
            return new StringExpr(new StringLiteral(se.SL.spelling, clonePos(se.SL.position)), clonePos(se.position));
        }
        if (e instanceof NullExpr ne) {
            return new NullExpr(clonePos(ne.position));
        }
        if (e instanceof NewExpr ne) {
            Ident id = new Ident(ne.I.spelling, clonePos(ne.I.position));
            id.decl = ne.I.decl;
            return new NewExpr(id, clonePos(ne.position));
        }
        if (e instanceof BinaryExpr be) {
            return new BinaryExpr(cloneExpr(be.E1), new Operator(be.O.spelling, clonePos(be.O.position)), cloneExpr(be.E2), clonePos(be.position));
        }
        if (e instanceof AssignExpr ae) {
            return new AssignExpr(cloneExpr(ae.E1), cloneExpr(ae.E2), clonePos(ae.position));
        }
        if (e instanceof CallExpr ce) {
            return new CallExpr(cloneExpr(ce.E), ce.AL, clonePos(ce.position));
        }
        if (e instanceof EmptyExpr) {
            return new EmptyExpr(clonePos(e.position));
        }
        return e;
    }

    private ASTList<Decl> appendDeclLists(ASTList<Decl> left, ASTList<Decl> right) {
        if (left instanceof EmptyDeclList) {
            return right;
        }
        DeclList dl = (DeclList) left;
        return new DeclList(dl.getHead(), appendDeclLists(dl.getNext(), right), dl.position);
    }

    Ident parseIdent() throws SyntaxError {
        if (currentToken.kind != Token.ID) {
            throw syntacticError("identifier expected here", "");
        }
        String spelling = currentToken.spelling;
        accept();
        return new Ident(spelling, previousTokenPosition);
    }

    Operator acceptOperator() throws SyntaxError {
        String spelling = currentToken.spelling;
        accept();
        return new Operator(spelling, previousTokenPosition);
    }

    private IntLiteral parseIntLiteral() throws SyntaxError {
        if (currentToken.kind == Token.INTLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            return new IntLiteral(spelling, previousTokenPosition);
        }
        throw syntacticError("integer literal expected here", "");
    }

    private FloatLiteral parseFloatLiteral() throws SyntaxError {
        if (currentToken.kind == Token.FLOATLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            return new FloatLiteral(spelling, previousTokenPosition);
        }
        throw syntacticError("float literal expected here", "");
    }

    private BooleanLiteral parseBooleanLiteral() throws SyntaxError {
        if (currentToken.kind == Token.BOOLEANLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            return new BooleanLiteral(spelling, previousTokenPosition);
        }
        throw syntacticError("boolean literal expected here", "");
    }

    private StringLiteral parseStringLiteral() throws SyntaxError {
        if (currentToken.kind == Token.STRINGLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            return new StringLiteral(spelling, previousTokenPosition);
        }
        throw syntacticError("string literal expected here", "");
    }

    private CharLiteral parseCharLiteral() throws SyntaxError {
        if (currentToken.kind == Token.CHARLITERAL) {
            String spelling = currentToken.spelling;
            accept();
            return new CharLiteral(spelling, previousTokenPosition);
        }
        throw syntacticError("char literal expected here", "");
    }
}
