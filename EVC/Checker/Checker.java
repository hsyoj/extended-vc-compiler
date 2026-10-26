package EVC.Checker;

import EVC.ASTs.*;
import EVC.Scanner.SourcePosition;
import EVC.ErrorReporter;
import EVC.StdEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Checker implements Visitor {

    private enum ErrorMessage {
        MISSING_MAIN("*0: main function is missing"),
        MAIN_RETURN_TYPE_NOT_INT("*1: return type of main is not int"),
        IDENTIFIER_REDECLARED("*2: identifier redeclared"),
        IDENTIFIER_DECLARED_VOID("*3: identifier declared void"),
        IDENTIFIER_DECLARED_VOID_ARRAY("*4: identifier declared void[]"),
        IDENTIFIER_UNDECLARED("*5: identifier undeclared"),
        INCOMPATIBLE_TYPE_FOR_ASSIGNMENT("*6: incompatible type for ="),
        INVALID_LVALUE_IN_ASSIGNMENT("*7: invalid lvalue in assignment"),
        INCOMPATIBLE_TYPE_FOR_RETURN("*8: incompatible type for return"),
        INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR("*9: incompatible type for this binary operator"),
        INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR("*10: incompatible type for this unary operator"),
        ARRAY_FUNCTION_AS_SCALAR("*11: attempt to use an array/function as a scalar"),
        SCALAR_FUNCTION_AS_ARRAY("*12: attempt to use a scalar/function as an array"),
        WRONG_TYPE_FOR_ARRAY_INITIALISER("*13: wrong type for element in array initialiser"),
        INVALID_INITIALISER_ARRAY_FOR_SCALAR("*14: invalid initialiser: array initialiser for scalar"),
        INVALID_INITIALISER_SCALAR_FOR_ARRAY("*15: invalid initialiser: scalar initialiser for array"),
        EXCESS_ELEMENTS_IN_ARRAY_INITIALISER("*16: excess elements in array initialiser"),
        ARRAY_SUBSCRIPT_NOT_INTEGER("*17: array subscript is not an integer"),
        ARRAY_SIZE_MISSING("*18: array size missing"),
        SCALAR_ARRAY_AS_FUNCTION("*19: attempt to reference a scalar/array as a function"),
        IF_CONDITIONAL_NOT_BOOLEAN("*20: if conditional is not boolean (found: %)"),
        FOR_CONDITIONAL_NOT_BOOLEAN("*21: for conditional is not boolean (found: %)"),
        WHILE_CONDITIONAL_NOT_BOOLEAN("*22: while conditional is not boolean (found: %)"),
        BREAK_NOT_IN_LOOP("*23: break must be in a while/for"),
        CONTINUE_NOT_IN_LOOP("*24: continue must be in a while/for"),
        TOO_MANY_ACTUAL_PARAMETERS("*25: too many actual parameters"),
        TOO_FEW_ACTUAL_PARAMETERS("*26: too few actual parameters"),
        WRONG_TYPE_FOR_ACTUAL_PARAMETER("*27: wrong type for actual parameter"),
        STATEMENTS_NOT_REACHED("*30: statement(s) not reached"),
        MISSING_RETURN_STATEMENT("*31: missing return statement"),
        STRUCT_HAS_NO_MEMBER("*32: struct has no member named %"),
        LEFT_SIDE_OF_ARROW_NOT_POINTER_TO_STRUCT("*33: left side of '->' is not a pointer to struct"),
        LEFT_SIDE_OF_DOT_NOT_STRUCT("*34: left side of '.' is not a struct"),
        FUNCTION_DECLARED_BUT_NOT_DEFINED("*35: function % declared but not defined"),
        NO_MATCHING_FUNCTION("*36: no matching function for call to %"),
        AMBIGUOUS_CALL("*37: ambiguous call to function %"),
        INVALID_FUNCTION_RETURN_TYPE("*38: invalid function return type"),
        INVALID_PARAMETER_TYPE("*39: invalid parameter type"),
        INVALID_ADDRESS_OF_OPERAND("*40: invalid operand for &"),
        NULL_POINTER_ACCESS("*41: null pointer access"),
        INVALID_NULLPTR_TARGET("*42: nullptr can only be assigned to or compared with pointers");

        private final String message;

        ErrorMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final int NO_CONVERSION = 0;
    private static final int INF_CONVERSION = 1_000_000;
    private static final SourcePosition dummyPos = new SourcePosition();
    private static final Ident dummyI = new Ident("x", dummyPos);

    private final SymbolTable idTable;
    private final ErrorReporter reporter;

    private FuncDecl currentFunction = null;
    private int loopDepth = 0;
    private boolean currentFunctionHasReturn = false;
    private final Map<String, List<FuncDecl>> functionOverloads = new HashMap<>();

    private static final class ResolvedCall {
        final FuncDecl function;
        final List<Arg> actualNodes;
        final List<Type> actualTypes;

        ResolvedCall(FuncDecl function, List<Arg> actualNodes, List<Type> actualTypes) {
            this.function = function;
            this.actualNodes = actualNodes;
            this.actualTypes = actualTypes;
        }
    }

    public Checker(ErrorReporter reporter) {
        this.reporter = Objects.requireNonNull(reporter, "ErrorReporter must not be null");
        this.idTable = new SymbolTable();
        establishStdEnvironment();
    }

    public void check(AST ast) {
        ast.visit(this, null);
    }

    private String getTypeName(Type type) {
        if (type == null) return "unknown";
        if (type instanceof IntType) return "int";
        if (type instanceof FloatType) return "float";
        if (type instanceof BooleanType) return "boolean";
        if (type instanceof CharType) return "char";
        if (type instanceof VoidType) return "void";
        if (type instanceof StringType) return "string";
        if (type instanceof PointerType) return type.toString();
        if (type instanceof ArrayType) return type.toString();
        if (type instanceof StructType) return type.toString();
        if (type instanceof ErrorType) return "error";
        return type.toString();
    }

    private boolean isNumeric(Type t) {
        return t instanceof IntType || t instanceof FloatType || t instanceof CharType;
    }

    private boolean isIntegral(Type t) {
        return t instanceof IntType || t instanceof CharType;
    }

    private boolean isBooleanish(Type t) {
        return t instanceof BooleanType || t instanceof IntType || t instanceof CharType;
    }

    private boolean isVoidPointer(Type t) {
        return t instanceof PointerType pt && pt.T instanceof VoidType;
    }

    private boolean sameType(Type a, Type b) {
        if (a == null || b == null) return false;
        if (a instanceof ErrorType || b instanceof ErrorType) return true;

        if (a.getClass() != b.getClass()) return false;

        if (a instanceof PointerType pa && b instanceof PointerType pb) {
            return sameType(pa.T, pb.T);
        }
        if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
            return sameType(aa.T, ab.T);
        }
        if (a instanceof StructType sa && b instanceof StructType sb) {
            return sa.I.spelling.equals(sb.I.spelling);
        }
        return true;
    }

    private boolean sameParameterTypes(FuncDecl a, FuncDecl b) {
        return sameParameterTypes(a.PL, b.PL);
    }

    private boolean sameParameterTypes(ASTList<Decl> pa, ASTList<Decl> pb) {
        ASTList<Decl> left = pa;
        ASTList<Decl> right = pb;
        while (left instanceof ParaList l && right instanceof ParaList r) {
            if (!sameType(l.getHead().T, r.getHead().T)) return false;
            left = l.getNext();
            right = r.getNext();
        }
        return left instanceof EmptyParaList && right instanceof EmptyParaList;
    }

    private boolean pointerBaseCompatible(Type expectedBase, Type actualBase) {
        return sameType(expectedBase, actualBase) || expectedBase instanceof VoidType || actualBase instanceof VoidType;
    }

    private int conversionCost(Type expected, Type actual) {
        if (expected == null || actual == null) return INF_CONVERSION;
        if (expected instanceof ErrorType || actual instanceof ErrorType) return NO_CONVERSION;
        if (sameType(expected, actual)) return NO_CONVERSION;

        if (expected instanceof FloatType) {
            if (actual instanceof IntType) return 1;
            if (actual instanceof CharType) return 2;
        }

        if (expected instanceof IntType) {
            if (actual instanceof CharType) return 1;
        }

        if (expected instanceof BooleanType) {
            if (actual instanceof IntType || actual instanceof CharType) return 1;
        }

        if (expected instanceof PointerType ep) {
            if (actual instanceof PointerType ap && pointerBaseCompatible(ep.T, ap.T)) {
                return sameType(ep.T, ap.T) ? NO_CONVERSION : 2;
            }
            if (actual instanceof ArrayType aa && pointerBaseCompatible(ep.T, aa.T)) {
                return 1;
            }
            if (actual instanceof StringType && ep.T instanceof CharType) {
                return 3;
            }
        }

        if (expected instanceof ArrayType ea) {
            if (actual instanceof ArrayType aa && sameType(ea.T, aa.T)) return NO_CONVERSION;
            if (actual instanceof StringType && ea.T instanceof CharType) return 2;
        }

        return INF_CONVERSION;
    }

    private boolean isCompatible(Type expected, Type actual) {
        return conversionCost(expected, actual) < INF_CONVERSION;
    }

    private void markCoercion(Expr expr, Type expected, Type actual) {
        if (expr == null || expected == null || actual == null) return;
        if (expected instanceof ErrorType || actual instanceof ErrorType) return;
        if (conversionCost(expected, actual) > NO_CONVERSION) {
            expr.coercionType = expected;
        }
    }

    private void report(ErrorMessage message, String token, SourcePosition pos) {
        reporter.reportError(message.getMessage(), token, pos);
    }

    private StructDefDecl getStructDefinition(StructType st) {
        Optional<IdEntry> entry = idTable.retrieve(st.I.spelling);
        if (entry.isPresent() && entry.get().attr instanceof StructDefDecl def) {
            return def;
        }
        return null;
    }

    private Decl findMember(ASTList<Decl> fields, String memberName) {
        ASTList<Decl> current = fields;
        while (current instanceof DeclList dl) {
            Decl d = dl.getHead();
            if (d.I != null && d.I.spelling.equals(memberName)) return d;
            current = dl.getNext();
        }
        return null;
    }

    private void declareVariable(Ident ident, Decl decl) {
        String name = ident.spelling;
        Optional<IdEntry> existing = idTable.retrieveOneLevel(name);
        if (existing.isPresent()) {
            report(ErrorMessage.IDENTIFIER_REDECLARED, name, ident.position);
            ident.decl = existing.get().attr;
            return;
        }
        idTable.insert(name, decl);
        ident.decl = decl;
    }

    private void declareFunction(FuncDecl func) {
        String name = func.I.spelling;
        List<FuncDecl> overloads = functionOverloads.computeIfAbsent(name, k -> new ArrayList<>());

        for (int i = 0; i < overloads.size(); i++) {
            FuncDecl existing = overloads.get(i);
            if (!sameParameterTypes(existing, func)) continue;

            if (!sameType(existing.T, func.T)) {
                report(ErrorMessage.IDENTIFIER_REDECLARED, name, func.I.position);
                func.I.decl = existing;
                return;
            }

            boolean existingPrototype = existing.S instanceof EmptyStmt;
            boolean newPrototype = func.S instanceof EmptyStmt;

            if (existingPrototype && !newPrototype) {
                overloads.set(i, func);
                idTable.insert(name, func);
                func.I.decl = func;
                return;
            }

            if (!existingPrototype && newPrototype) {
                func.I.decl = existing;
                return;
            }

            report(ErrorMessage.IDENTIFIER_REDECLARED, name, func.I.position);
            func.I.decl = existing;
            return;
        }

        overloads.add(func);
        idTable.insert(name, func);
        func.I.decl = func;
    }

    private FuncDecl selectMainFunction() {
        List<FuncDecl> mains = functionOverloads.get("main");
        if (mains == null) return null;

        FuncDecl chosen = null;
        for (FuncDecl fd : mains) {
            if (!(fd.S instanceof EmptyStmt)) {
                chosen = fd;
                break;
            }
        }
        if (chosen == null && !mains.isEmpty()) {
            chosen = mains.get(0);
        }
        return chosen;
    }

    private void checkVarDeclType(Ident ident, Type type) {
        if (type instanceof VoidType) {
            report(ErrorMessage.IDENTIFIER_DECLARED_VOID, ident.spelling, ident.position);
            return;
        }
        if (type instanceof ArrayType at) {
            if (at.T instanceof VoidType) {
                report(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY, ident.spelling, ident.position);
            }
        }
    }

    private void checkFunctionSignature(FuncDecl ast) {
        Type returnType = (Type) ast.T.visit(this, null);

        if (returnType instanceof ArrayType) {
            report(ErrorMessage.INVALID_FUNCTION_RETURN_TYPE, ast.I.spelling, ast.I.position);
        }

        ASTList<Decl> params = ast.PL;
        while (params instanceof ParaList pl) {
            ParaDecl pd = (ParaDecl) pl.getHead();
            Type paramType = (Type) pd.T.visit(this, null);
            if (paramType instanceof VoidType) {
                report(ErrorMessage.INVALID_PARAMETER_TYPE, pd.I.spelling, pd.I.position);
            }
            if (paramType instanceof ArrayType at && at.T instanceof VoidType) {
                report(ErrorMessage.IDENTIFIER_DECLARED_VOID_ARRAY, pd.I.spelling, pd.I.position);
            }
            params = pl.getNext();
        }
    }

    private boolean definitelyTerminates(Stmt stmt) {
        if (stmt instanceof ReturnStmt || stmt instanceof BreakStmt || stmt instanceof ContinueStmt) {
            return true;
        }
        if (stmt instanceof CompoundStmt compound) {
            ASTList<Stmt> current = compound.SL;
            while (current instanceof StmtList sl) {
                if (definitelyTerminates(sl.getHead())) return true;
                current = sl.getNext();
            }
        }
        return false;
    }

    private Type ensureBooleanContext(Expr expr, ErrorMessage error) {
        Type condType = (Type) expr.visit(this, null);
        if (condType instanceof ErrorType) return condType;
        if (condType instanceof BooleanType) return condType;
        if (condType instanceof IntType || condType instanceof CharType) {
            markCoercion(expr, StdEnvironment.booleanType, condType);
            return StdEnvironment.booleanType;
        }
        report(error, getTypeName(condType), expr.position);
        return StdEnvironment.errorType;
    }

    private boolean isLValue(Expr expr) {
        if (expr instanceof VarExpr varExpr) {
            if (varExpr.V instanceof SimpleVar sv) {
                AST binding = sv.I.decl;
                if (!(binding instanceof Decl decl)) return false;
                if (binding instanceof FuncDecl) return false;
                return !(decl.T instanceof ArrayType);
            }
            return true;
        }
        if (expr instanceof ArrayExpr || expr instanceof MemberExpr || expr instanceof ArrowExpr) {
            return true;
        }
        if (expr instanceof UnaryExpr ue && ue.O.spelling.equals("*")) {
            return !(ue.type instanceof ErrorType);
        }
        return false;
    }

    private boolean isAddressable(Expr expr) {
        if (expr instanceof VarExpr varExpr && varExpr.V instanceof SimpleVar sv) {
            AST binding = sv.I.decl;
            return binding instanceof Decl && !(binding instanceof FuncDecl);
        }
        return expr instanceof ArrayExpr || expr instanceof MemberExpr || expr instanceof ArrowExpr ||
               (expr instanceof UnaryExpr ue && ue.O.spelling.equals("*"));
    }

    private boolean isNullPtrLiteral(Expr expr) {
        return expr instanceof NullExpr;
    }

    private void checkAssignmentCompatibility(Type expected, Expr rhs, Type actual, SourcePosition pos) {
        if (!isCompatible(expected, actual)) {
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT, "", pos);
            return;
        }
        markCoercion(rhs, expected, actual);
    }

    private int arraySize(ArrayType arrayType) {
        if (arrayType.E instanceof IntExpr ie) {
            return Integer.parseInt(ie.IL.spelling);
        }
        return -1;
    }

    private int checkArrayInitializer(ArrayInitExpr init, ArrayType expectedArray, String ownerName) {
        Type elementType = expectedArray.T;
        int expectedSize = arraySize(expectedArray);
        int count = 0;

        ASTList<?> current = init.IL;
        while (current instanceof ArrayExprList ael) {
            Expr expr = (Expr) ael.getHead();

            if (elementType instanceof ArrayType nestedArray) {
                if (expr instanceof ArrayInitExpr nestedInit) {
                    checkArrayInitializer(nestedInit, nestedArray, ownerName);
                } else {
                    Type actual = (Type) expr.visit(this, null);
                    report(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY, ownerName, expr.position);
                    if (actual != null && !(actual instanceof ErrorType)) {
                        // keep going
                    }
                }
            } else {
                if (expr instanceof ArrayInitExpr) {
                    report(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR, ownerName, expr.position);
                } else {
                    Type actual = (Type) expr.visit(this, null);
                    if (!isCompatible(elementType, actual)) {
                        report(ErrorMessage.WRONG_TYPE_FOR_ARRAY_INITIALISER,
                               "at position " + count, expr.position);
                    } else {
                        markCoercion(expr, elementType, actual);
                    }
                }
            }

            count++;
            current = ael.getNext();
        }

        if (expectedSize != -1 && count > expectedSize) {
            report(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER, ownerName, init.position);
        }
        return count;
    }

    private int stringLiteralLogicalLength(String raw) {
        int count = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length()) {
                i++;
            }
            count++;
        }
        return count;
    }

    private boolean isCharArray(Type t) {
        return t instanceof ArrayType at && at.T instanceof CharType;
    }

    private boolean isStringLike(Type t) {
        return t instanceof StringType || isCharArray(t);
    }

    private boolean isWholeCharArrayTarget(Expr expr, Type t) {
        if (!isCharArray(t)) return false;
        return expr instanceof VarExpr || expr instanceof MemberExpr || expr instanceof ArrowExpr;
    }

    private boolean handleCharArrayStringInitializer(Decl ast, ArrayType arrayType, Expr initExpr) {
        if (!(arrayType.T instanceof CharType) || !(initExpr instanceof StringExpr se)) {
            return false;
        }

        int needed = stringLiteralLogicalLength(se.SL.spelling) + 1;
        int declared = arraySize(arrayType);

        if (arrayType.E instanceof EmptyExpr) {
            SourcePosition p = se.position != null ? se.position : ast.position;
            IntLiteral sizeLit = new IntLiteral(Integer.toString(needed), p);
            IntExpr sizeExpr = new IntExpr(sizeLit, p);
            arrayType.E = sizeExpr;
            sizeExpr.parent = arrayType;
        } else if (declared != -1 && declared < needed) {
            report(ErrorMessage.EXCESS_ELEMENTS_IN_ARRAY_INITIALISER, ast.I.spelling, initExpr.position);
        }

        return true;
    }

    private void checkVarInit(Decl ast) {
        Type declaredType = ast.T;
        Expr initExpr = (ast instanceof GlobalVarDecl) ? ((GlobalVarDecl) ast).E : ((LocalVarDecl) ast).E;

        if (declaredType instanceof ArrayType arrayType) {
            if (arrayType.E instanceof EmptyExpr && initExpr instanceof EmptyExpr) {
                report(ErrorMessage.ARRAY_SIZE_MISSING, ast.I.spelling, ast.I.position);
                return;
            }

            if (initExpr instanceof EmptyExpr) {
                return;
            }

            if (handleCharArrayStringInitializer(ast, arrayType, initExpr)) {
                return;
            }

            if (!(initExpr instanceof ArrayInitExpr arrayInit)) {
                report(ErrorMessage.INVALID_INITIALISER_SCALAR_FOR_ARRAY, ast.I.spelling, initExpr.position);
                return;
            }

            checkArrayInitializer(arrayInit, arrayType, ast.I.spelling);
            return;
        }

        if (initExpr instanceof ArrayInitExpr) {
            report(ErrorMessage.INVALID_INITIALISER_ARRAY_FOR_SCALAR, ast.I.spelling, initExpr.position);
            return;
        }

        if (initExpr instanceof EmptyExpr) return;

        Type initType = (Type) initExpr.visit(this, null);
        checkAssignmentCompatibility(declaredType, initExpr, initType, initExpr.position);
    }

    private List<Arg> collectArgs(ASTList<Arg> args) {
        List<Arg> result = new ArrayList<>();
        ASTList<Arg> current = args;
        while (current instanceof ArgList al) {
            result.add(al.getHead());
            current = al.getNext();
        }
        return result;
    }

    private List<ParaDecl> collectParams(ASTList<Decl> params) {
        List<ParaDecl> result = new ArrayList<>();
        ASTList<Decl> current = params;
        while (current instanceof ParaList pl) {
            result.add((ParaDecl) pl.getHead());
            current = pl.getNext();
        }
        return result;
    }

    private int[] conversionVector(FuncDecl candidate, List<Type> actualTypes) {
        List<ParaDecl> formals = collectParams(candidate.PL);
        if (formals.size() != actualTypes.size()) return null;
        int[] vec = new int[formals.size()];
        for (int i = 0; i < formals.size(); i++) {
            int cost = conversionCost(formals.get(i).T, actualTypes.get(i));
            if (cost >= INF_CONVERSION) return null;
            vec[i] = cost;
        }
        return vec;
    }

    private boolean betterConversionVector(int[] a, int[] b) {
        boolean strictlyBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i]) return false;
            if (a[i] < b[i]) strictlyBetter = true;
        }
        return strictlyBetter;
    }

    private int totalConversionCost(int[] vec) {
        int sum = 0;
        for (int v : vec) sum += v;
        return sum;
    }

    private ResolvedCall resolveOverload(String name, ASTList<Arg> actuals, SourcePosition pos) {
        List<FuncDecl> candidates = functionOverloads.getOrDefault(name, List.of());

        List<Arg> actualNodes = collectArgs(actuals);
        List<Type> actualTypes = new ArrayList<>(actualNodes.size());
        for (Arg arg : actualNodes) {
            actualTypes.add((Type) arg.visit(this, null));
        }

        List<FuncDecl> viableFns = new ArrayList<>();
        List<int[]> viableVectors = new ArrayList<>();
        for (FuncDecl candidate : candidates) {
            int[] vec = conversionVector(candidate, actualTypes);
            if (vec != null) {
                viableFns.add(candidate);
                viableVectors.add(vec);
            }
        }

        if (viableFns.isEmpty()) {
            report(ErrorMessage.NO_MATCHING_FUNCTION, name, pos);
            return null;
        }

        List<Integer> maximal = new ArrayList<>();
        for (int i = 0; i < viableFns.size(); i++) {
            boolean dominated = false;
            for (int j = 0; j < viableFns.size(); j++) {
                if (i == j) continue;
                if (betterConversionVector(viableVectors.get(j), viableVectors.get(i))) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) maximal.add(i);
        }

        if (maximal.size() != 1) {
            int bestTotal = Integer.MAX_VALUE;
            List<Integer> bestByTotal = new ArrayList<>();
            for (int idx : maximal) {
                int total = totalConversionCost(viableVectors.get(idx));
                if (total < bestTotal) {
                    bestTotal = total;
                    bestByTotal.clear();
                    bestByTotal.add(idx);
                } else if (total == bestTotal) {
                    bestByTotal.add(idx);
                }
            }
            if (bestByTotal.size() != 1) {
                report(ErrorMessage.AMBIGUOUS_CALL, name, pos);
                return null;
            }
            maximal = bestByTotal;
        }

        FuncDecl best = viableFns.get(maximal.get(0));
        List<ParaDecl> formals = collectParams(best.PL);
        for (int i = 0; i < formals.size(); i++) {
            markCoercion(actualNodes.get(i).E, formals.get(i).T, actualTypes.get(i));
        }

        return new ResolvedCall(best, actualNodes, actualTypes);
    }

    @Override
    public Object visitProgram(Program ast, Object o) {
        ast.FL.visit(this, null);

        for (List<FuncDecl> overloads : functionOverloads.values()) {
            for (FuncDecl fd : overloads) {
                if (fd.position == dummyPos) continue;
                if (fd.S instanceof EmptyStmt) {
                    report(ErrorMessage.FUNCTION_DECLARED_BUT_NOT_DEFINED, fd.I.spelling, fd.position);
                }
            }
        }

        FuncDecl main = selectMainFunction();
        if (main == null) {
            report(ErrorMessage.MISSING_MAIN, "", ast.position);
        } else if (!(main.T instanceof IntType)) {
            report(ErrorMessage.MAIN_RETURN_TYPE_NOT_INT, "", main.position);
        }

        return null;
    }

    @Override
    public Object visitDeclList(DeclList ast, Object o) {
        ast.getHead().visit(this, null);
        ast.getNext().visit(this, null);
        return null;
    }

    @Override
    public Object visitEmptyDeclList(EmptyDeclList ast, Object o) {
        return null;
    }

    @Override
    public Object visitFuncDecl(FuncDecl ast, Object o) {
        checkFunctionSignature(ast);
        declareFunction(ast);

        if (ast.S instanceof EmptyStmt) {
            return null;
        }

        currentFunction = ast;
        currentFunctionHasReturn = false;
        ast.S.visit(this, ast);

        if (!(ast.T instanceof VoidType || ast.T instanceof ErrorType) && !currentFunctionHasReturn) {
            report(ErrorMessage.MISSING_RETURN_STATEMENT, "", ast.position);
        }

        currentFunction = null;
        return null;
    }

    @Override
    public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) {
        ast.T.visit(this, null);
        declareVariable(ast.I, ast);
        checkVarDeclType(ast.I, ast.T);
        checkVarInit(ast);
        return null;
    }

    @Override
    public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
        ast.T.visit(this, null);
        declareVariable(ast.I, ast);
        checkVarDeclType(ast.I, ast.T);
        checkVarInit(ast);
        return null;
    }

    @Override
    public Object visitStructDefDecl(StructDefDecl ast, Object o) {
        Ident structIdent = ast.S.I;
        Optional<IdEntry> existing = idTable.retrieveOneLevel(structIdent.spelling);
        if (existing.isPresent()) {
            report(ErrorMessage.IDENTIFIER_REDECLARED, structIdent.spelling, structIdent.position);
        } else {
            idTable.insert(structIdent.spelling, ast);
            structIdent.decl = ast;
        }

        idTable.openScope();
        ast.Fields.visit(this, null);
        idTable.closeScope();
        return null;
    }

    @Override
    public Object visitStmtList(StmtList ast, Object o) {
        ast.getHead().visit(this, o);
        if (definitelyTerminates(ast.getHead()) && ast.getNext() instanceof StmtList) {
            report(ErrorMessage.STATEMENTS_NOT_REACHED, "", ast.getNext().position);
        }
        ast.getNext().visit(this, o);
        return null;
    }

    @Override
    public Object visitEmptyStmtList(EmptyStmtList ast, Object o) {
        return null;
    }

    @Override
    public Object visitCompoundStmt(CompoundStmt ast, Object o) {
        idTable.openScope();

        if (o instanceof FuncDecl fd) {
            fd.PL.visit(this, null);
        }

        ast.SL.visit(this, null);

        idTable.closeScope();
        return null;
    }

    @Override
    public Object visitExprStmt(ExprStmt ast, Object o) {
        ast.E.visit(this, null);
        return null;
    }

    @Override
    public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) {
        return null;
    }

    @Override
    public Object visitEmptyStmt(EmptyStmt ast, Object o) {
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt ast, Object o) {
        ensureBooleanContext(ast.E, ErrorMessage.IF_CONDITIONAL_NOT_BOOLEAN);
        ast.S1.visit(this, o);
        ast.S2.visit(this, o);
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt ast, Object o) {
        ensureBooleanContext(ast.E, ErrorMessage.WHILE_CONDITIONAL_NOT_BOOLEAN);
        loopDepth++;
        ast.S.visit(this, o);
        loopDepth--;
        return null;
    }

    @Override
    public Object visitForStmt(ForStmt ast, Object o) {
        ast.E1.visit(this, null);
        if (!(ast.E2 instanceof EmptyExpr)) {
            ensureBooleanContext(ast.E2, ErrorMessage.FOR_CONDITIONAL_NOT_BOOLEAN);
        }
        ast.E3.visit(this, null);

        loopDepth++;
        ast.S.visit(this, o);
        loopDepth--;
        return null;
    }

    @Override
    public Object visitBreakStmt(BreakStmt ast, Object o) {
        if (loopDepth == 0) {
            report(ErrorMessage.BREAK_NOT_IN_LOOP, "", ast.position);
        }
        return null;
    }

    @Override
    public Object visitContinueStmt(ContinueStmt ast, Object o) {
        if (loopDepth == 0) {
            report(ErrorMessage.CONTINUE_NOT_IN_LOOP, "", ast.position);
        }
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt ast, Object o) {
        currentFunctionHasReturn = true;
        Type returnExprType = (Type) ast.E.visit(this, null);

        if (currentFunction == null) return null;

        if (ast.E instanceof EmptyExpr) {
            if (!(currentFunction.T instanceof VoidType)) {
                report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN, "", ast.position);
            }
            return null;
        }

        if (!isCompatible(currentFunction.T, returnExprType)) {
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_RETURN, "", ast.position);
        } else {
            markCoercion(ast.E, currentFunction.T, returnExprType);
        }
        return null;
    }

    @Override
    public Object visitDeclStmt(DeclStmt ast, Object o) {
        ast.D.visit(this, null);
        return null;
    }

    @Override
    public Object visitEmptyExpr(EmptyExpr ast, Object o) {
        ast.type = (ast.parent instanceof ReturnStmt) ? StdEnvironment.voidType : StdEnvironment.errorType;
        return ast.type;
    }

    @Override
    public Object visitBooleanExpr(BooleanExpr ast, Object o) {
        ast.type = StdEnvironment.booleanType;
        return ast.type;
    }

    @Override
    public Object visitIntExpr(IntExpr ast, Object o) {
        ast.type = StdEnvironment.intType;
        return ast.type;
    }

    @Override
    public Object visitFloatExpr(FloatExpr ast, Object o) {
        ast.type = StdEnvironment.floatType;
        return ast.type;
    }

    @Override
    public Object visitStringExpr(StringExpr ast, Object o) {
        ast.type = StdEnvironment.stringType;
        return ast.type;
    }

    @Override
    public Object visitNewExpr(NewExpr ast, Object o) {
        StructType st = new StructType(new Ident(ast.I.spelling, ast.I.position), ast.position);
        StructDefDecl def = getStructDefinition(st);
        if (def == null) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, ast.I.spelling, ast.I.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }
        ast.I.decl = def;
        ast.type = new PointerType(st, ast.position);
        return ast.type;
    }

    @Override
    public Object visitNullExpr(NullExpr ast, Object o) {
        ast.type = new PointerType(StdEnvironment.voidType, ast.position);
        return ast.type;
    }

    @Override
    public Object visitCharExpr(CharExpr ast, Object o) {
        ast.type = StdEnvironment.charType;
        return ast.type;
    }

    @Override
    public Object visitPostfixExpr(PostfixExpr ast, Object o) {
        Type baseType = (Type) ast.E.visit(this, null);
        String op = ast.O.spelling;

        if (!op.equals("++") && !op.equals("--")) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!isLValue(ast.E)) {
            report(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (baseType instanceof IntType) {
            ast.O.spelling = "i" + op;
            ast.type = StdEnvironment.intType;
            return ast.type;
        }
        if (baseType instanceof FloatType) {
            ast.O.spelling = "f" + op;
            ast.type = StdEnvironment.floatType;
            return ast.type;
        }
        if (baseType instanceof PointerType) {
            ast.O.spelling = "p" + op;
            ast.type = baseType;
            return ast.type;
        }

        report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
        ast.type = StdEnvironment.errorType;
        return ast.type;
    }

    @Override
    public Object visitMemberExpr(MemberExpr ast, Object o) {
        Type baseType = (Type) ast.StructExpr.visit(this, null);

        if (isNullPtrLiteral(ast.StructExpr)) {
            report(ErrorMessage.NULL_POINTER_ACCESS, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (baseType instanceof ErrorType) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!(baseType instanceof StructType st)) {
            report(ErrorMessage.LEFT_SIDE_OF_DOT_NOT_STRUCT, "", ast.StructExpr.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        StructDefDecl def = getStructDefinition(st);
        if (def == null) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, st.I.spelling, st.I.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        Decl member = findMember(def.Fields, ast.Field.spelling);
        if (member == null) {
            report(ErrorMessage.STRUCT_HAS_NO_MEMBER, ast.Field.spelling, ast.Field.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        ast.Field.decl = member;
        ast.type = member.T;
        return ast.type;
    }

    @Override
    public Object visitArrowExpr(ArrowExpr ast, Object o) {
        Type ptrType = (Type) ast.PtrExpr.visit(this, null);

        if (isNullPtrLiteral(ast.PtrExpr)) {
            report(ErrorMessage.NULL_POINTER_ACCESS, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (ptrType instanceof ErrorType) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!(ptrType instanceof PointerType pt && pt.T instanceof StructType st)) {
            report(ErrorMessage.LEFT_SIDE_OF_ARROW_NOT_POINTER_TO_STRUCT, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        StructDefDecl def = getStructDefinition(st);
        if (def == null) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, st.I.spelling, st.I.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        Decl member = findMember(def.Fields, ast.Field.spelling);
        if (member == null) {
            report(ErrorMessage.STRUCT_HAS_NO_MEMBER, ast.Field.spelling, ast.Field.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        ast.Field.decl = member;
        ast.type = member.T;
        return ast.type;
    }

    @Override
    public Object visitVarExpr(VarExpr ast, Object o) {
        Type t = (Type) ast.V.visit(this, null);

        if (ast.V instanceof SimpleVar simpleVar && simpleVar.I.decl instanceof FuncDecl) {
            boolean usedAsCallee = ast.parent instanceof CallExpr call && call.E == ast;
            if (!usedAsCallee) {
                report(ErrorMessage.ARRAY_FUNCTION_AS_SCALAR, simpleVar.I.spelling, ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }
        }

        ast.type = t;
        return ast.type;
    }

    @Override
    public Object visitAssignExpr(AssignExpr ast, Object o) {
        Type leftType = (Type) ast.E1.visit(this, null);
        Type rightType = (Type) ast.E2.visit(this, null);

        if (isWholeCharArrayTarget(ast.E1, leftType) && isStringLike(rightType)) {
            if (!isCompatible(leftType, rightType)) {
                report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT, "", ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }
            ast.type = leftType;
            return ast.type;
        }

        if (isNullPtrLiteral(ast.E2) && !(leftType instanceof PointerType || leftType instanceof ArrayType)) {
            report(ErrorMessage.INVALID_NULLPTR_TARGET, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!isLValue(ast.E1)) {
            report(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!isCompatible(leftType, rightType)) {
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_ASSIGNMENT, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        markCoercion(ast.E2, leftType, rightType);
        ast.type = leftType;
        return ast.type;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr ast, Object o) {
        Type left = (Type) ast.E1.visit(this, null);
        Type right = (Type) ast.E2.visit(this, null);
        String op = ast.O.spelling;

        if (left instanceof ErrorType || right instanceof ErrorType) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/") || op.equals("%")) {
            if (op.equals("%")) {
                if (isIntegral(left) && isIntegral(right)) {
                    markCoercion(ast.E1, StdEnvironment.intType, left);
                    markCoercion(ast.E2, StdEnvironment.intType, right);
                    ast.O.spelling = "i%";
                    ast.type = StdEnvironment.intType;
                    return ast.type;
                }
                report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }

            if (isNumeric(left) && isNumeric(right)) {
                boolean isFloat = left instanceof FloatType || right instanceof FloatType;
                if (isFloat) {
                    markCoercion(ast.E1, StdEnvironment.floatType, left);
                    markCoercion(ast.E2, StdEnvironment.floatType, right);
                    ast.O.spelling = "f" + op;
                    ast.type = StdEnvironment.floatType;
                } else {
                    markCoercion(ast.E1, StdEnvironment.intType, left);
                    markCoercion(ast.E2, StdEnvironment.intType, right);
                    ast.O.spelling = "i" + op;
                    ast.type = StdEnvironment.intType;
                }
                return ast.type;
            }

            Type leftPtrLike = left instanceof ArrayType la ? new PointerType(la.T, ast.E1.position) : left;
            Type rightPtrLike = right instanceof ArrayType ra ? new PointerType(ra.T, ast.E2.position) : right;
            if (left instanceof ArrayType) ast.E1.coercionType = leftPtrLike;
            if (right instanceof ArrayType) ast.E2.coercionType = rightPtrLike;

            if ((op.equals("+") || op.equals("-")) && leftPtrLike instanceof PointerType && isIntegral(right)) {
                markCoercion(ast.E2, StdEnvironment.intType, right);
                ast.O.spelling = "p" + op;
                ast.type = leftPtrLike;
                return ast.type;
            }

            if (op.equals("+") && isIntegral(left) && rightPtrLike instanceof PointerType) {
                markCoercion(ast.E1, StdEnvironment.intType, left);
                ast.O.spelling = "p+";
                ast.type = rightPtrLike;
                return ast.type;
            }

            if (op.equals("-") && leftPtrLike instanceof PointerType lp && rightPtrLike instanceof PointerType rp &&
                pointerBaseCompatible(lp.T, rp.T)) {
                ast.O.spelling = "pp-";
                ast.type = StdEnvironment.intType;
                return ast.type;
            }

            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=")) {
            if (isNumeric(left) && isNumeric(right)) {
                boolean isFloat = left instanceof FloatType || right instanceof FloatType;
                if (isFloat) {
                    markCoercion(ast.E1, StdEnvironment.floatType, left);
                    markCoercion(ast.E2, StdEnvironment.floatType, right);
                    ast.O.spelling = "f" + op;
                } else {
                    markCoercion(ast.E1, StdEnvironment.intType, left);
                    markCoercion(ast.E2, StdEnvironment.intType, right);
                    ast.O.spelling = "i" + op;
                }
                ast.type = StdEnvironment.booleanType;
                return ast.type;
            }

            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("==") || op.equals("!=")) {
            if (left instanceof BooleanType || right instanceof BooleanType) {
                if (isBooleanish(left) && isBooleanish(right)) {
                    markCoercion(ast.E1, StdEnvironment.booleanType, left);
                    markCoercion(ast.E2, StdEnvironment.booleanType, right);
                    ast.O.spelling = "i" + op;
                    ast.type = StdEnvironment.booleanType;
                    return ast.type;
                }
            }

            if (isNumeric(left) && isNumeric(right)) {
                boolean isFloat = left instanceof FloatType || right instanceof FloatType;
                if (isFloat) {
                    markCoercion(ast.E1, StdEnvironment.floatType, left);
                    markCoercion(ast.E2, StdEnvironment.floatType, right);
                    ast.O.spelling = "f" + op;
                } else {
                    markCoercion(ast.E1, StdEnvironment.intType, left);
                    markCoercion(ast.E2, StdEnvironment.intType, right);
                    ast.O.spelling = "i" + op;
                }
                ast.type = StdEnvironment.booleanType;
                return ast.type;
            }

            if ((left instanceof PointerType || left instanceof ArrayType) &&
                (right instanceof PointerType || right instanceof ArrayType)) {
                Type leftPtr = (left instanceof ArrayType la) ? new PointerType(la.T, ast.E1.position) : left;
                Type rightPtr = (right instanceof ArrayType ra) ? new PointerType(ra.T, ast.E2.position) : right;
                if (leftPtr instanceof PointerType lp && rightPtr instanceof PointerType rp &&
                    pointerBaseCompatible(lp.T, rp.T)) {
                    if (left instanceof ArrayType) ast.E1.coercionType = leftPtr;
                    if (right instanceof ArrayType) ast.E2.coercionType = rightPtr;
                    ast.O.spelling = "p" + op;
                    ast.type = StdEnvironment.booleanType;
                    return ast.type;
                }
            }

            if (isNullPtrLiteral(ast.E1) || isNullPtrLiteral(ast.E2)) {
                Type other = isNullPtrLiteral(ast.E1) ? right : left;
                if (other instanceof PointerType || other instanceof ArrayType) {
                    ast.O.spelling = "p" + op;
                    ast.type = StdEnvironment.booleanType;
                    return ast.type;
                }
                report(ErrorMessage.INVALID_NULLPTR_TARGET, "", ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }

            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("<<") || op.equals(">>") || op.equals("&") || op.equals("|") || op.equals("^")) {
            if (isIntegral(left) && isIntegral(right)) {
                markCoercion(ast.E1, StdEnvironment.intType, left);
                markCoercion(ast.E2, StdEnvironment.intType, right);
                ast.O.spelling = "i" + op;
                ast.type = StdEnvironment.intType;
                return ast.type;
            }
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("&&") || op.equals("||")) {
            if (isBooleanish(left) && isBooleanish(right)) {
                markCoercion(ast.E1, StdEnvironment.booleanType, left);
                markCoercion(ast.E2, StdEnvironment.booleanType, right);
                ast.O.spelling = "i" + op;
                ast.type = StdEnvironment.booleanType;
                return ast.type;
            }

            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_BINARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        ast.type = StdEnvironment.errorType;
        return ast.type;
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr ast, Object o) {
        Type operandType = (Type) ast.E.visit(this, null);
        String op = ast.O.spelling;

        if (operandType instanceof ErrorType) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("+") || op.equals("-")) {
            if (operandType instanceof FloatType) {
                ast.O.spelling = "f" + op;
                ast.type = StdEnvironment.floatType;
                return ast.type;
            }
            if (operandType instanceof IntType || operandType instanceof CharType) {
                markCoercion(ast.E, StdEnvironment.intType, operandType);
                ast.O.spelling = "i" + op;
                ast.type = StdEnvironment.intType;
                return ast.type;
            }
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("!")) {
            if (isBooleanish(operandType)) {
                markCoercion(ast.E, StdEnvironment.booleanType, operandType);
                ast.O.spelling = "i!";
                ast.type = StdEnvironment.booleanType;
                return ast.type;
            }
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("~")) {
            if (isIntegral(operandType)) {
                markCoercion(ast.E, StdEnvironment.intType, operandType);
                ast.O.spelling = "i~";
                ast.type = StdEnvironment.intType;
                return ast.type;
            }
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("&")) {
            if (!isAddressable(ast.E)) {
                report(ErrorMessage.INVALID_ADDRESS_OF_OPERAND, "", ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }
            ast.type = new PointerType(operandType, ast.position);
            return ast.type;
        }

        if (op.equals("*")) {
            if (isNullPtrLiteral(ast.E)) {
                report(ErrorMessage.NULL_POINTER_ACCESS, "", ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }
            if (operandType instanceof PointerType pt) {
                ast.type = pt.T;
                return ast.type;
            }
            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (op.equals("++") || op.equals("--")) {
            if (!isLValue(ast.E)) {
                report(ErrorMessage.INVALID_LVALUE_IN_ASSIGNMENT, "", ast.position);
                ast.type = StdEnvironment.errorType;
                return ast.type;
            }

            if (operandType instanceof IntType) {
                ast.O.spelling = "i" + op;
                ast.type = StdEnvironment.intType;
                return ast.type;
            }
            if (operandType instanceof FloatType) {
                ast.O.spelling = "f" + op;
                ast.type = StdEnvironment.floatType;
                return ast.type;
            }
            if (operandType instanceof PointerType) {
                ast.O.spelling = "p" + op;
                ast.type = operandType;
                return ast.type;
            }

            report(ErrorMessage.INCOMPATIBLE_TYPE_FOR_UNARY_OPERATOR, op, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        ast.type = StdEnvironment.errorType;
        return ast.type;
    }

    @Override
    public Object visitCallExpr(CallExpr ast, Object o) {
        if (!(ast.E instanceof VarExpr varExpr) || !(varExpr.V instanceof SimpleVar simpleVar)) {
            report(ErrorMessage.SCALAR_ARRAY_AS_FUNCTION, "", ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        String name = simpleVar.I.spelling;
        Optional<IdEntry> entry = idTable.retrieve(name);
        if (entry.isEmpty()) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, name, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        if (!(entry.get().attr instanceof FuncDecl)) {
            report(ErrorMessage.SCALAR_ARRAY_AS_FUNCTION, name, ast.position);
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        ResolvedCall resolved = resolveOverload(name, ast.AL, ast.position);
        if (resolved == null) {
            ast.type = StdEnvironment.errorType;
            return ast.type;
        }

        simpleVar.I.decl = resolved.function;
        ast.type = resolved.function.T;
        return ast.type;
    }

    @Override
    public Object visitArrayExpr(ArrayExpr ast, Object o) {
        Type baseType = (Type) ast.V.visit(this, null);
        Type indexType = (Type) ast.E.visit(this, null);

        if (indexType instanceof CharType) {
            markCoercion(ast.E, StdEnvironment.intType, indexType);
            indexType = StdEnvironment.intType;
        }

        if (!(indexType instanceof IntType || indexType instanceof ErrorType)) {
            report(ErrorMessage.ARRAY_SUBSCRIPT_NOT_INTEGER, "", ast.position);
        }

        if (baseType instanceof ArrayType at) {
            ast.type = at.T;
            return ast.type;
        }

        if (baseType instanceof PointerType pt) {
            ast.type = pt.T;
            return ast.type;
        }

        report(ErrorMessage.SCALAR_FUNCTION_AS_ARRAY, "", ast.position);
        ast.type = StdEnvironment.errorType;
        return ast.type;
    }

    @Override
    public Object visitParaList(ParaList ast, Object o) {
        ast.getHead().visit(this, null);
        ast.getNext().visit(this, null);
        return null;
    }

    @Override
    public Object visitParaDecl(ParaDecl ast, Object o) {
        ast.T.visit(this, null);
        declareVariable(ast.I, ast);
        checkVarDeclType(ast.I, ast.T);
        return null;
    }

    @Override
    public Object visitEmptyParaList(EmptyParaList ast, Object o) {
        return null;
    }

    @Override
    public Object visitArgList(ArgList ast, Object o) {
        ast.getHead().visit(this, null);
        ast.getNext().visit(this, null);
        return null;
    }

    @Override
    public Object visitArg(Arg ast, Object o) {
        return ast.E.visit(this, null);
    }

    @Override
    public Object visitEmptyArgList(EmptyArgList ast, Object o) {
        return null;
    }

    @Override
    public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) {
        if (!(o instanceof Decl decl) || !(decl.T instanceof ArrayType arrayType)) {
            return StdEnvironment.errorType;
        }
        checkArrayInitializer(ast, arrayType, decl.I.spelling);
        return arrayType;
    }

    @Override
    public Object visitArrayExprList(ArrayExprList ast, Object o) {
        return null;
    }

    @Override
    public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) {
        return null;
    }

    @Override
    public Object visitErrorType(ErrorType ast, Object o) {
        return StdEnvironment.errorType;
    }

    @Override
    public Object visitCharType(CharType ast, Object o) {
        return StdEnvironment.charType;
    }

    @Override
    public Object visitPointerType(PointerType ast, Object o) {
        ast.T.visit(this, null);
        return ast;
    }

    @Override
    public Object visitBooleanType(BooleanType ast, Object o) {
        return StdEnvironment.booleanType;
    }

    @Override
    public Object visitIntType(IntType ast, Object o) {
        return StdEnvironment.intType;
    }

    @Override
    public Object visitFloatType(FloatType ast, Object o) {
        return StdEnvironment.floatType;
    }

    @Override
    public Object visitStringType(StringType ast, Object o) {
        return StdEnvironment.stringType;
    }

    @Override
    public Object visitVoidType(VoidType ast, Object o) {
        return StdEnvironment.voidType;
    }

    @Override
    public Object visitArrayType(ArrayType ast, Object o) {
        ast.T.visit(this, null);
        return ast;
    }

    @Override
    public Object visitStructType(StructType ast, Object o) {
        Optional<IdEntry> entry = idTable.retrieve(ast.I.spelling);
        if (entry.isEmpty() || !(entry.get().attr instanceof StructDefDecl)) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, ast.I.spelling, ast.I.position);
            return StdEnvironment.errorType;
        }
        ast.I.decl = entry.get().attr;
        return ast;
    }

    @Override
    public Object visitIdent(Ident ast, Object o) {
        Optional<IdEntry> binding = idTable.retrieve(ast.spelling);
        binding.ifPresent(entry -> ast.decl = entry.attr);
        return binding.map(entry -> entry.attr).orElse(null);
    }

    @Override
    public Object visitSimpleVar(SimpleVar ast, Object o) {
        ast.I.visit(this, null);
        AST binding = ast.I.decl;

        if (binding == null) {
            report(ErrorMessage.IDENTIFIER_UNDECLARED, ast.I.spelling, ast.position);
            return StdEnvironment.errorType;
        }

        if (binding instanceof Decl decl) {
            return decl.T;
        }

        return StdEnvironment.errorType;
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral ast, Object o) {
        return StdEnvironment.booleanType;
    }

    @Override
    public Object visitIntLiteral(IntLiteral ast, Object o) {
        return StdEnvironment.intType;
    }

    @Override
    public Object visitFloatLiteral(FloatLiteral ast, Object o) {
        return StdEnvironment.floatType;
    }

    @Override
    public Object visitStringLiteral(StringLiteral ast, Object o) {
        return StdEnvironment.stringType;
    }

    @Override
    public Object visitCharLiteral(CharLiteral ast, Object o) {
        return StdEnvironment.charType;
    }

    @Override
    public Object visitOperator(Operator ast, Object o) {
        return null;
    }

    private FuncDecl declareStdFunc(Type resultType, String id, ASTList<Decl> pl) {
        FuncDecl binding = new FuncDecl(resultType, new Ident(id, dummyPos), pl, new EmptyStmt(dummyPos), dummyPos);
        functionOverloads.computeIfAbsent(id, k -> new ArrayList<>()).add(binding);
        idTable.insert(id, binding);
        return binding;
    }

    private ParaList createParaList(Type paramType) {
        return new ParaList(
                new ParaDecl(paramType, new Ident("x", dummyPos), dummyPos),
                new EmptyParaList(dummyPos),
                dummyPos
        );
    }

    private ArrayType createCharArrayType() {
        return new ArrayType(StdEnvironment.charType, new EmptyExpr(dummyPos), dummyPos);
    }

    private PointerType createCharPointerType() {
        return new PointerType(StdEnvironment.charType, dummyPos);
    }

    private void establishStdEnvironment() {
        StdEnvironment.booleanType = new BooleanType(dummyPos);
        StdEnvironment.intType = new IntType(dummyPos);
        StdEnvironment.floatType = new FloatType(dummyPos);
        StdEnvironment.stringType = new StringType(dummyPos);
        StdEnvironment.voidType = new VoidType(dummyPos);
        StdEnvironment.errorType = new ErrorType(dummyPos);
        StdEnvironment.charType = new CharType(dummyPos);

        StdEnvironment.getIntDecl = declareStdFunc(StdEnvironment.intType, "getInt", new EmptyParaList(dummyPos));
        StdEnvironment.getIntPromptDecl = declareStdFunc(StdEnvironment.intType, "getInt", createParaList(StdEnvironment.stringType));
        StdEnvironment.getIntPromptCharArrayDecl = declareStdFunc(StdEnvironment.intType, "getInt", createParaList(createCharArrayType()));
        StdEnvironment.getIntPromptCharPointerDecl = declareStdFunc(StdEnvironment.intType, "getInt", createParaList(createCharPointerType()));

        StdEnvironment.getFloatDecl = declareStdFunc(StdEnvironment.floatType, "getFloat", new EmptyParaList(dummyPos));
        StdEnvironment.getFloatPromptDecl = declareStdFunc(StdEnvironment.floatType, "getFloat", createParaList(StdEnvironment.stringType));

        StdEnvironment.getCharDecl = declareStdFunc(StdEnvironment.charType, "getChar", new EmptyParaList(dummyPos));
        StdEnvironment.getCharPromptDecl = declareStdFunc(StdEnvironment.charType, "getChar", createParaList(StdEnvironment.stringType));

        StdEnvironment.getStringDecl = declareStdFunc(StdEnvironment.stringType, "getString", new EmptyParaList(dummyPos));
        StdEnvironment.getStringPromptDecl = declareStdFunc(StdEnvironment.stringType, "getString", createParaList(StdEnvironment.stringType));
        StdEnvironment.readStringDecl = declareStdFunc(StdEnvironment.intType, "getString", createParaList(createCharArrayType()));
        StdEnvironment.readStringPromptDecl = declareStdFunc(StdEnvironment.intType, "getString", new ParaList(
                new ParaDecl(createCharArrayType(), new Ident("buf", dummyPos), dummyPos),
                new ParaList(new ParaDecl(StdEnvironment.stringType, new Ident("prompt", dummyPos), dummyPos), new EmptyParaList(dummyPos), dummyPos),
                dummyPos));
        declareStdFunc(StdEnvironment.intType, "getString", createParaList(createCharPointerType()));
        declareStdFunc(StdEnvironment.intType, "getString", new ParaList(
                new ParaDecl(createCharPointerType(), new Ident("buf", dummyPos), dummyPos),
                new ParaList(new ParaDecl(StdEnvironment.stringType, new Ident("prompt", dummyPos), dummyPos), new EmptyParaList(dummyPos), dummyPos),
                dummyPos));
        StdEnvironment.ordDecl = declareStdFunc(StdEnvironment.intType, "ord", createParaList(StdEnvironment.charType));
        StdEnvironment.chrDecl = declareStdFunc(StdEnvironment.charType, "chr", createParaList(StdEnvironment.intType));

        StdEnvironment.printIntDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(StdEnvironment.intType));
        StdEnvironment.printlnIntDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(StdEnvironment.intType));

        StdEnvironment.printFloatDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(StdEnvironment.floatType));
        StdEnvironment.printlnFloatDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(StdEnvironment.floatType));

        StdEnvironment.printBooleanDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(StdEnvironment.booleanType));
        StdEnvironment.printlnBooleanDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(StdEnvironment.booleanType));

        StdEnvironment.printCharDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(StdEnvironment.charType));
        StdEnvironment.printlnCharDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(StdEnvironment.charType));
        StdEnvironment.printCharArrayDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(createCharArrayType()));
        declareStdFunc(StdEnvironment.voidType, "print", createParaList(createCharPointerType()));
        StdEnvironment.printlnCharArrayDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(createCharArrayType()));
        declareStdFunc(StdEnvironment.voidType, "println", createParaList(createCharPointerType()));

        StdEnvironment.printStringDecl = declareStdFunc(StdEnvironment.voidType, "print", createParaList(StdEnvironment.stringType));
        StdEnvironment.printlnStringDecl = declareStdFunc(StdEnvironment.voidType, "println", createParaList(StdEnvironment.stringType));

        StdEnvironment.printlnDecl = declareStdFunc(StdEnvironment.voidType, "println", new EmptyParaList(dummyPos));
    }
}
