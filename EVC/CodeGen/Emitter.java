package EVC.CodeGen;

import EVC.ASTs.*;
import EVC.ErrorReporter;
import EVC.StdEnvironment;
import EVC.Scanner.SourcePosition;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public final class Emitter implements Visitor {

    private static final int DEFAULT_STACK_LIMIT = 256;
    private static final int DEFAULT_LOCAL_LIMIT = 256;
    private static final int TMP_OLDI = 239;
    private static final int TMP_OLDA = 240;
    private static final int TMP_OLDF = 241;
    private static final int TMP_PTR0 = 242;
    private static final int TMP_PTR1 = 243;
    private static final int TMP_OBJ0 = 244;
    private static final int TMP_OBJ1 = 245;
    private static final int TMP_INT0 = 246;
    private static final int TMP_INT1 = 247;
    private static final int TMP_FLOAT0 = 248;
    private static final int TMP_VALI = 249;
    private static final int TMP_VALA = 250;

    private final ErrorReporter errorReporter;
    private final String inputFilename;
    private final String classname;

    private Frame currentFrame;
    private List<String> currentMethod;
    private FuncDecl currentFunction;
    private Program currentProgram;

    public Emitter(String inputFilename, ErrorReporter reporter) {
        this.inputFilename = inputFilename;
        this.errorReporter = reporter;

        String base = new File(inputFilename).getName();
        int dot = base.lastIndexOf('.');
        String rawClassname = dot > 0 ? base.substring(0, dot) : base;
        this.classname = sanitizeClassName(rawClassname);
    }

    private String sanitizeClassName(String raw) {
        if (raw == null || raw.isEmpty()) return "_";
        StringBuilder sb = new StringBuilder();
        char first = raw.charAt(0);
        if (Character.isJavaIdentifierStart(first)) {
            sb.append(first);
        } else {
            sb.append('_');
            sb.append(Character.isJavaIdentifierPart(first) ? first : '_');
        }
        for (int i = 1; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            sb.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        return sb.toString();
    }

    public void gen(AST ast) {
        currentProgram = (Program) ast;
        ast.visit(this, null);
        JVM.dump(classname + ".j");
    }

    // ------------------------------------------------------------------
    // Generic emitting helpers
    // ------------------------------------------------------------------

    private void emitTop(String line) {
        JVM.append(new Instruction(line));
    }

    private void emit(String line) {
        if (currentMethod != null) {
            currentMethod.add(line);
        } else {
            emitTop(line);
        }
    }

    private void emit(String op, String arg) { emit(op + " " + arg); }
    private void emit(String op, int arg) { emit(op + " " + arg); }
    private void emit(String op, String arg1, String arg2) { emit(op + " " + arg1 + " " + arg2); }

    private void beginMethod(String header, Frame frame) {
        currentFrame = frame;
        currentMethod = new ArrayList<>();
        emitTop(".method " + header);
        emitTop(".limit stack " + DEFAULT_STACK_LIMIT);
        emitTop(".limit locals " + DEFAULT_LOCAL_LIMIT);
    }

    private List<String> optimizeMethodLines(List<String> lines) {
        List<String> work = new ArrayList<>(lines);
        boolean changed;
        do {
            changed = false;
            List<String> next = new ArrayList<>();
            for (int i = 0; i < work.size(); i++) {
                String cur = work.get(i);
                String trimmed = cur.trim();
                String nxt = i + 1 < work.size() ? work.get(i + 1).trim() : null;

                if (trimmed.startsWith("goto ") && nxt != null && nxt.equals(trimmed.substring(5) + ":")) {
                    changed = true;
                    continue;
                }
                if (trimmed.equals("dup") && "pop".equals(nxt)) {
                    changed = true;
                    i++;
                    continue;
                }
                if ((trimmed.startsWith("iload") || trimmed.startsWith("fload") || trimmed.startsWith("aload") || trimmed.startsWith("iconst") || trimmed.startsWith("bipush") || trimmed.startsWith("sipush") || trimmed.startsWith("ldc "))
                        && "pop".equals(nxt)) {
                    changed = true;
                    i++;
                    continue;
                }
                if ((trimmed.equals("iconst_0") || trimmed.equals("iconst_1")) && nxt != null && (nxt.startsWith("ifeq ") || nxt.startsWith("ifne "))) {
                    boolean one = trimmed.equals("iconst_1");
                    if (nxt.startsWith("ifeq ")) {
                        if (!one) next.add("goto " + nxt.substring(5));
                    } else {
                        if (one) next.add("goto " + nxt.substring(5));
                    }
                    changed = true;
                    i++;
                    continue;
                }
                next.add(cur);
                if (trimmed.startsWith("goto ") || trimmed.equals("return") || trimmed.equals("ireturn") || trimmed.equals("freturn") || trimmed.equals("areturn")) {
                    while (i + 1 < work.size() && !work.get(i + 1).trim().endsWith(":")) {
                        i++;
                        changed = true;
                    }
                }
            }
            work = next;
        } while (changed);
        return work;
    }

    private void endMethod() {
        if (currentMethod != null) {
            for (String line : optimizeMethodLines(currentMethod)) {
                emitTop(line);
            }
            emitTop(".end method");
            emitTop("");
        }
        currentMethod = null;
        currentFrame = null;
    }

    private String newLabel() {
        return currentFrame.getNewLabel();
    }

    private void emitLabel(String label) {
        emit(label + ":");
    }

    private void emitICONST(int value) {
        if (value == -1) emit("iconst_m1");
        else if (value >= 0 && value <= 5) emit("iconst_" + value);
        else if (value >= -128 && value <= 127) emit("bipush", value);
        else if (value >= -32768 && value <= 32767) emit("sipush", value);
        else emit("ldc", Integer.toString(value));
    }

    private void emitFCONST(float value) {
        if (value == 0.0f) emit("fconst_0");
        else if (value == 1.0f) emit("fconst_1");
        else if (value == 2.0f) emit("fconst_2");
        else emit("ldc", Float.toString(value));
    }

    private void emitILOAD(int index) { emit(index >= 0 && index <= 3 ? "iload_" + index : "iload " + index); }
    private void emitISTORE(int index) { emit(index >= 0 && index <= 3 ? "istore_" + index : "istore " + index); }
    private void emitFLOAD(int index) { emit(index >= 0 && index <= 3 ? "fload_" + index : "fload " + index); }
    private void emitFSTORE(int index) { emit(index >= 0 && index <= 3 ? "fstore_" + index : "fstore " + index); }
    private void emitALOAD(int index) { emit(index >= 0 && index <= 3 ? "aload_" + index : "aload " + index); }
    private void emitASTORE(int index) { emit(index >= 0 && index <= 3 ? "astore_" + index : "astore " + index); }

    private String fieldDescriptor(Type t) {
        if (t instanceof IntType) return "I";
        if (t instanceof FloatType) return "F";
        if (t instanceof BooleanType) return "Z";
        if (t instanceof CharType) return "C";
        if (t instanceof VoidType) return "V";
        if (t instanceof StringType) return "Ljava/lang/String;";
        if (t instanceof PointerType) return "LEVCPtr;";
        if (t instanceof StructType st) return "L" + st.I.spelling + ";";
        if (t instanceof ArrayType at) return "[" + fieldDescriptor(at.T);
        if (t instanceof ErrorType) return "V";
        return "Ljava/lang/Object;";
    }

    private boolean isReferenceType(Type t) {
        return t instanceof ArrayType || t instanceof StructType || t instanceof PointerType || t instanceof StringType;
    }

    private boolean isPrimitiveLike(Type t) {
        return t instanceof IntType || t instanceof FloatType || t instanceof BooleanType || t instanceof CharType;
    }

    private String arrayNewInstructionElement(Type elementType) {
        if (elementType instanceof IntType) return "int";
        if (elementType instanceof FloatType) return "float";
        if (elementType instanceof BooleanType) return "boolean";
        if (elementType instanceof CharType) return "char";
        return null;
    }

    private String arrayElementClass(Type elementType) {
        if (elementType instanceof StructType st) return st.I.spelling;
        if (elementType instanceof StringType) return "java/lang/String";
        if (elementType instanceof PointerType) return "EVCPtr";
        if (elementType instanceof ArrayType) return fieldDescriptor(elementType);
        return null;
    }

    private boolean canArrayDefaultRecurse(Type elementType) {
        return elementType instanceof ArrayType || elementType instanceof StructType;
    }

    private int declaredArraySize(ArrayType at) {
        if (at.E instanceof IntExpr ie) {
            return Integer.parseInt(ie.IL.spelling);
        }
        return -1;
    }

    private int initializerListLength(ASTList<Expr> list) {
        int n = 0;
        ASTList<Expr> cur = list;
        while (cur instanceof ArrayExprList ael) {
            n++;
            cur = ael.getNext();
        }
        return n;
    }

    private Type expressionValueType(Expr expr) {
        return expr.coercionType != null ? expr.coercionType : expr.type;
    }

    private boolean isCharArrayType(Type t) {
        return t instanceof ArrayType at && at.T instanceof CharType;
    }

    private boolean isStringLike(Type t) {
        return t instanceof StringType || isCharArrayType(t);
    }

    private void emitCoercion(Type from, Type to) {
        if (from == null || to == null) return;
        if (from instanceof ErrorType || to instanceof ErrorType) return;
        if (from.getClass().equals(to.getClass())) return;

        if ((from instanceof IntType || from instanceof CharType) && to instanceof FloatType) {
            emit("i2f");
            return;
        }

        if ((from instanceof IntType || from instanceof CharType) && to instanceof BooleanType) {
            String trueLabel = newLabel();
            String endLabel = newLabel();
            emit("ifne", trueLabel);
            emit("iconst_0");
            emit("goto", endLabel);
            emitLabel(trueLabel);
            emit("iconst_1");
            emitLabel(endLabel);
            return;
        }

        if (from instanceof StringType && isCharArrayType(to)) {
            emit("invokestatic EVC/lang/system/toCharArray(Ljava/lang/String;)[C");
            return;
        }

        if (from instanceof StringType && to instanceof PointerType pt && pt.T instanceof CharType) {
            emit("invokestatic EVC/lang/system/toCharArray(Ljava/lang/String;)[C");
            emitASTORE(TMP_OBJ0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emit("iconst_0");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        if (from instanceof ArrayType && to instanceof PointerType) {
            emitASTORE(TMP_OBJ0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emit("iconst_0");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        // char -> int is a JVM no-op.
    }

    private void emitExpr(Expr expr) {
        expr.visit(this, currentFrame);
        if (expr.coercionType != null) {
            emitCoercion(expr.type, expr.coercionType);
        }
    }

    private String decodeEscapedString(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    case '\'' -> sb.append('\'');
                    default -> sb.append(n);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private void emitCharArrayFromString(ArrayType targetType, StringExpr initExpr) {
        String decoded = decodeEscapedString(initExpr.SL.spelling);
        int declared = declaredArraySize(targetType);
        int size = declared >= 0 ? declared : decoded.length() + 1;

        emitICONST(size);
        emit("newarray char");
        emitASTORE(TMP_OBJ0);

        for (int i = 0; i < decoded.length() && i < size; i++) {
            emitALOAD(TMP_OBJ0);
            emitICONST(i);
            emitICONST(decoded.charAt(i));
            emit("castore");
        }

        if (decoded.length() < size) {
            emitALOAD(TMP_OBJ0);
            emitICONST(decoded.length());
            emitICONST(0);
            emit("castore");
        }

        emitALOAD(TMP_OBJ0);
    }

    private void emitStringConstant(String raw) {
        String decoded = decodeEscapedString(raw);
        String escaped = decoded
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
        emit("ldc", "\"" + escaped + "\"");
    }
    private int decodeCharLiteral(CharLiteral ast) {
        String s = ast.spelling;
        if (s.length() == 1) return s.charAt(0);
        if (s.equals("\\n")) return '\n';
        if (s.equals("\\t")) return '\t';
        if (s.equals("\\r")) return '\r';
        if (s.equals("\\b")) return '\b';
        if (s.equals("\\f")) return '\f';
        if (s.equals("\\\\")) return '\\';
        if (s.equals("\\\"")) return '"';
        if (s.equals("\\'")) return '\'';
        return s.isEmpty() ? 0 : s.charAt(0);
    }

    private int indexOf(Decl decl) {
        if (decl instanceof ParaDecl pd) return pd.index;
        if (decl instanceof LocalVarDecl ld) return ld.index;
        return -1;
    }

    private void emitDefaultValue(Type t) {
        if (t instanceof FloatType) {
            emit("fconst_0");
        } else if (t instanceof IntType || t instanceof BooleanType || t instanceof CharType) {
            emit("iconst_0");
        } else if (t instanceof StructType st) {
            emit("new", st.I.spelling);
            emit("dup");
            emit("invokespecial", st.I.spelling + "/<init>()V");
        } else if (t instanceof ArrayType at) {
            emitAllocateArrayDefault(at);
        } else {
            emit("aconst_null");
        }
    }

    private void emitAllocateArrayDefault(ArrayType at) {
        emitAllocateArrayDefault(at, TMP_OBJ0);
    }

    private void emitAllocateArrayDefault(ArrayType at, int tempSlot) {
        int size = declaredArraySize(at);
        if (size < 0) {
            emit("aconst_null");
            return;
        }

        emitICONST(size);
        String primitiveElement = arrayNewInstructionElement(at.T);
        if (primitiveElement != null) {
            emit("newarray", primitiveElement);
            return;
        }

        String refClass = arrayElementClass(at.T);
        emit("anewarray", refClass);

        if (canArrayDefaultRecurse(at.T)) {
            emitASTORE(tempSlot);
            for (int i = 0; i < size; i++) {
                emitALOAD(tempSlot);
                emitICONST(i);
                if (at.T instanceof ArrayType nested) emitAllocateArrayDefault(nested, tempSlot - 1);
                else emitDefaultValue(at.T);
                emit("aastore");
            }
            emitALOAD(tempSlot);
        }
    }

    private void emitArrayInitializer(ArrayType targetType, ArrayInitExpr initExpr) {
        emitArrayInitializer(targetType, initExpr, TMP_OBJ0);
    }

    private void emitArrayInitializer(ArrayType targetType, ArrayInitExpr initExpr, int tempSlot) {
        int declared = declaredArraySize(targetType);
        int size = declared >= 0 ? declared : initializerListLength(initExpr.IL);

        emitICONST(size);
        String primitiveElement = arrayNewInstructionElement(targetType.T);
        if (primitiveElement != null) {
            emit("newarray", primitiveElement);
        } else {
            emit("anewarray", arrayElementClass(targetType.T));
        }

        emitASTORE(tempSlot);

        int index = 0;
        ASTList<Expr> cur = initExpr.IL;
        while (cur instanceof ArrayExprList ael) {
            Expr elem = ael.getHead();
            emitALOAD(tempSlot);
            emitICONST(index);
            if (elem instanceof ArrayInitExpr nested && targetType.T instanceof ArrayType nestedType) {
                emitArrayInitializer(nestedType, nested, tempSlot - 1);
                emit("aastore");
            } else {
                emitExpr(elem);
                emitStoreToArrayElement(targetType.T);
            }
            index++;
            cur = ael.getNext();
        }

        emitALOAD(tempSlot);
    }

    private void emitStoreToArrayElement(Type elemType) {
        if (elemType instanceof FloatType) emit("fastore");
        else if (elemType instanceof IntType) emit("iastore");
        else if (elemType instanceof BooleanType) emit("bastore");
        else if (elemType instanceof CharType) emit("castore");
        else emit("aastore");
    }

    private void emitLoadFromArrayElement(Type elemType) {
        if (elemType instanceof FloatType) emit("faload");
        else if (elemType instanceof IntType) emit("iaload");
        else if (elemType instanceof BooleanType) emit("baload");
        else if (elemType instanceof CharType) emit("caload");
        else emit("aaload");
    }

    private void emitLoadPointerValue(Type pointedType) {
        String arrayDesc = "[" + fieldDescriptor(pointedType);
        if (pointedType instanceof IntType || pointedType instanceof FloatType || pointedType instanceof BooleanType || pointedType instanceof CharType) {
            emitALOAD(TMP_OBJ0);
            emit("checkcast", arrayDesc);
            emitILOAD(TMP_INT0);
            emitLoadFromArrayElement(pointedType);
            return;
        }

        String useArray = newLabel();
        String done = newLabel();
        emitALOAD(TMP_OBJ0);
        emit("instanceof", arrayDesc);
        emit("ifne", useArray);

        emitALOAD(TMP_OBJ0);
        emit("checkcast", stripObjectDescriptor(fieldDescriptor(pointedType)));
        emit("goto", done);

        emitLabel(useArray);
        emitALOAD(TMP_OBJ0);
        emit("checkcast", arrayDesc);
        emitILOAD(TMP_INT0);
        emitLoadFromArrayElement(pointedType);
        emitLabel(done);
    }

    private void emitStorePointerValue(Type pointedType) {
        String arrayDesc = "[" + fieldDescriptor(pointedType);

        if (pointedType instanceof IntType || pointedType instanceof FloatType || pointedType instanceof BooleanType || pointedType instanceof CharType) {
            emitALOAD(TMP_OBJ0);
            emit("checkcast", arrayDesc);
            emitILOAD(TMP_INT0);
            if (pointedType instanceof FloatType) {
                emitFLOAD(TMP_FLOAT0);
            } else if (isReferenceType(pointedType)) {
                emitALOAD(TMP_VALA);
            } else {
                emitILOAD(TMP_VALI);
            }
            emitStoreToArrayElement(pointedType);
            return;
        }

        String useArray = newLabel();
        String done = newLabel();

        emitALOAD(TMP_OBJ0);
        emit("instanceof", arrayDesc);
        emit("ifne", useArray);

        emit("goto", done);

        emitLabel(useArray);
        emitALOAD(TMP_OBJ0);
        emit("checkcast", arrayDesc);
        emitILOAD(TMP_INT0);
        emitALOAD(TMP_VALA);
        emitStoreToArrayElement(pointedType);
        emitLabel(done);
    }

    private String stripObjectDescriptor(String desc) {
        if (desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1);
        }
        return desc;
    }

    private void emitPointerBaseAndOffset(Expr ptrExpr) {
        String nonNull = newLabel();
        String done = newLabel();
        emitExpr(ptrExpr);
        emitASTORE(TMP_PTR0);
        emitALOAD(TMP_PTR0);
        emit("ifnonnull", nonNull);
        emit("aconst_null");
        emitASTORE(TMP_OBJ0);
        emit("iconst_0");
        emitISTORE(TMP_INT0);
        emit("goto", done);
        emitLabel(nonNull);
        emitALOAD(TMP_PTR0);
        emit("getfield EVCPtr/base Ljava/lang/Object;");
        emitASTORE(TMP_OBJ0);
        emitALOAD(TMP_PTR0);
        emit("getfield EVCPtr/off I");
        emitISTORE(TMP_INT0);
        emitLabel(done);
    }

    private void emitPointerBaseAndOffsetWithIndex(Expr ptrExpr, Expr indexExpr) {
        emitPointerBaseAndOffset(ptrExpr);
        emitExpr(indexExpr);
        emitISTORE(TMP_INT1);
        emitILOAD(TMP_INT0);
        emitILOAD(TMP_INT1);
        emit("iadd");
        emitISTORE(TMP_INT0);
    }

    private void emitAddressOf(Expr e) {
        if (e instanceof VarExpr ve && ve.V instanceof SimpleVar sv) {
            Decl d = (Decl) sv.I.decl;
            if (d.T instanceof StructType || d.T instanceof ArrayType || d.T instanceof PointerType || d.T instanceof StringType) {
                emitExpr(e);
                emitASTORE(TMP_OBJ0);
                emit("new EVCPtr");
                emit("dup");
                emitALOAD(TMP_OBJ0);
                emit("iconst_0");
                emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
                return;
            }

            // Primitive variable address: wrap current value in a 1-element array.
            emitICONST(1);
            String primitiveKind = arrayNewInstructionElement(d.T);
            emit("newarray", primitiveKind);
            emit("dup");
            emit("iconst_0");
            emitExpr(e);
            emitStoreToArrayElement(d.T);
            emitASTORE(TMP_OBJ0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emit("iconst_0");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        if (e instanceof ArrayExpr ae) {
            if (ae.V.type instanceof PointerType) {
                emitPointerBaseAndOffsetWithIndex(ae.V, ae.E);
            } else {
                emitExpr(ae.V);
                emitASTORE(TMP_OBJ0);
                emitExpr(ae.E);
                emitISTORE(TMP_INT0);
            }
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emitILOAD(TMP_INT0);
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        if (e instanceof MemberExpr me) {
            // Fallback wrapper for address-of member.
            emitICONST(1);
            String primitiveKind = arrayNewInstructionElement(me.type);
            if (primitiveKind != null) emit("newarray", primitiveKind);
            else emit("anewarray", arrayElementClass(me.type));
            emit("dup");
            emit("iconst_0");
            emitExpr(e);
            emitStoreToArrayElement(me.type);
            emitASTORE(TMP_OBJ0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emit("iconst_0");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        if (e instanceof ArrowExpr ae) {
            // Fallback wrapper for address-of pointer-field access.
            emitICONST(1);
            String primitiveKind = arrayNewInstructionElement(ae.type);
            if (primitiveKind != null) emit("newarray", primitiveKind);
            else emit("anewarray", arrayElementClass(ae.type));
            emit("dup");
            emit("iconst_0");
            emitExpr(e);
            emitStoreToArrayElement(ae.type);
            emitASTORE(TMP_OBJ0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emit("iconst_0");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return;
        }

        emit("aconst_null");
    }

    private void emitStoreSimpleVar(Decl decl) {
        if (decl instanceof GlobalVarDecl gd) {
            emit("putstatic", classname + "/" + gd.I.spelling + " " + fieldDescriptor(gd.T));
        } else {
            int index = indexOf(decl);
            if (decl.T instanceof FloatType) emitFSTORE(index);
            else if (isReferenceType(decl.T)) emitASTORE(index);
            else emitISTORE(index);
        }
    }

    private void emitLoadSimpleVar(Decl decl) {
        if (decl instanceof GlobalVarDecl gd) {
            emit("getstatic", classname + "/" + gd.I.spelling + " " + fieldDescriptor(gd.T));
        } else {
            int index = indexOf(decl);
            if (decl.T instanceof FloatType) emitFLOAD(index);
            else if (isReferenceType(decl.T)) emitALOAD(index);
            else emitILOAD(index);
        }
    }

    private void emitStoreExprValueToTemp(Type t) {
        if (t instanceof FloatType) emitFSTORE(TMP_FLOAT0);
        else if (isReferenceType(t)) emitASTORE(TMP_VALA);
        else emitISTORE(TMP_VALI);
    }

    private void emitReloadExprValueFromTemp(Type t) {
        if (t instanceof FloatType) emitFLOAD(TMP_FLOAT0);
        else if (isReferenceType(t)) emitALOAD(TMP_VALA);
        else emitILOAD(TMP_VALI);
    }

    private void emitPutField(String owner, String name, Type t) {
        emit("putfield", owner + "/" + name + " " + fieldDescriptor(t));
    }

    private void emitGetField(String owner, String name, Type t) {
        emit("getfield", owner + "/" + name + " " + fieldDescriptor(t));
    }

    private String methodDescriptor(FuncDecl fd) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        ASTList<Decl> params = fd.PL;
        while (params instanceof ParaList pl) {
            sb.append(fieldDescriptor(pl.getHead().T));
            params = pl.getNext();
        }
        sb.append(')');
        sb.append(fieldDescriptor(fd.T));
        return sb.toString();
    }

    private void emitBuiltinMethods() {
        beginMethod("public static getInt()I", new Frame(false));
        emit("getstatic", classname + "/_scanner Ljava/util/Scanner;");
        emit("invokevirtual java/util/Scanner/nextInt()I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getInt(Ljava/lang/String;)I", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getInt(Ljava/lang/String;)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getInt([C)I", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getInt([C)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getInt(LEVCPtr;)I", new Frame(false));
        emitALOAD(0);
        emit("getfield EVCPtr/base Ljava/lang/Object;");
        emitALOAD(0);
        emit("getfield EVCPtr/off I");
        emit("invokestatic EVC/lang/system/getInt(Ljava/lang/Object;I)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getFloat()F", new Frame(false));
        emit("getstatic", classname + "/_scanner Ljava/util/Scanner;");
        emit("invokevirtual java/util/Scanner/nextFloat()F");
        emit("freturn");
        endMethod();

        beginMethod("public static getFloat(Ljava/lang/String;)F", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getFloat(Ljava/lang/String;)F");
        emit("freturn");
        endMethod();

        beginMethod("public static getChar()C", new Frame(false));
        emit("invokestatic EVC/lang/system/getChar()C");
        emit("ireturn");
        endMethod();

        beginMethod("public static getChar(Ljava/lang/String;)C", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getChar(Ljava/lang/String;)C");
        emit("ireturn");
        endMethod();

        beginMethod("public static getString()Ljava/lang/String;", new Frame(false));
        emit("invokestatic EVC/lang/system/getString()Ljava/lang/String;");
        emit("areturn");
        endMethod();

        beginMethod("public static getString(Ljava/lang/String;)Ljava/lang/String;", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getString(Ljava/lang/String;)Ljava/lang/String;");
        emit("areturn");
        endMethod();

        beginMethod("public static getString([C)I", new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/getString([C)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getString([CLjava/lang/String;)I", new Frame(false));
        emitALOAD(0);
        emitALOAD(1);
        emit("invokestatic EVC/lang/system/getString([CLjava/lang/String;)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getString(LEVCPtr;)I", new Frame(false));
        emitALOAD(0);
        emit("getfield EVCPtr/base Ljava/lang/Object;");
        emitALOAD(0);
        emit("getfield EVCPtr/off I");
        emit("invokestatic EVC/lang/system/getString(Ljava/lang/Object;I)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static getString(LEVCPtr;Ljava/lang/String;)I", new Frame(false));
        emitALOAD(0);
        emit("getfield EVCPtr/base Ljava/lang/Object;");
        emitALOAD(0);
        emit("getfield EVCPtr/off I");
        emitALOAD(1);
        emit("invokestatic EVC/lang/system/getString(Ljava/lang/Object;ILjava/lang/String;)I");
        emit("ireturn");
        endMethod();

        beginMethod("public static chr(I)C", new Frame(false));
        emitILOAD(0);
        emit("invokestatic EVC/lang/system/chr(I)C");
        emit("ireturn");
        endMethod();

        beginMethod("public static ord(C)I", new Frame(false));
        emitILOAD(0);
        emit("invokestatic EVC/lang/system/ord(C)I");
        emit("ireturn");
        endMethod();

        emitBuiltinPrint("print(I)V", "I", false);
        emitBuiltinPrint("println(I)V", "I", true);
        emitBuiltinPrint("print(F)V", "F", false);
        emitBuiltinPrint("println(F)V", "F", true);
        emitBuiltinPrint("print(Z)V", "Z", false);
        emitBuiltinPrint("println(Z)V", "Z", true);
        emitBuiltinPrint("print(C)V", "C", false);
        emitBuiltinPrint("println(C)V", "C", true);
        emitBuiltinPrint("print(Ljava/lang/String;)V", "Ljava/lang/String;", false);
        emitBuiltinPrint("println(Ljava/lang/String;)V", "Ljava/lang/String;", true);
        emitBuiltinRuntimeCharArrayPrint("print([C)V", false);
        emitBuiltinRuntimeCharArrayPrint("println([C)V", true);
        emitBuiltinRuntimeCharPointerPrint("print(LEVCPtr;)V", false);
        emitBuiltinRuntimeCharPointerPrint("println(LEVCPtr;)V", true);

        beginMethod("public static println()V", new Frame(false));
        emit("getstatic java/lang/System/out Ljava/io/PrintStream;");
        emit("invokevirtual java/io/PrintStream/println()V");
        emit("return");
        endMethod();
    }

    private void emitBuiltinPrint(String headerSuffix, String argDesc, boolean newLine) {
        beginMethod("public static " + headerSuffix, new Frame(false));
        emit("getstatic java/lang/System/out Ljava/io/PrintStream;");
        if (argDesc.equals("F")) emitFLOAD(0);
        else if (argDesc.startsWith("L") || argDesc.startsWith("[")) emitALOAD(0);
        else emitILOAD(0);
        emit("invokevirtual java/io/PrintStream/" + (newLine ? "println" : "print") + "(" + argDesc + ")V");
        emit("return");
        endMethod();
    }

    private void emitBuiltinRuntimeCharArrayPrint(String headerSuffix, boolean newLine) {
        beginMethod("public static " + headerSuffix, new Frame(false));
        emitALOAD(0);
        emit("invokestatic EVC/lang/system/" + (newLine ? "println" : "print") + "([C)V");
        emit("return");
        endMethod();
    }

    private void emitBuiltinRuntimeCharPointerPrint(String headerSuffix, boolean newLine) {
        beginMethod("public static " + headerSuffix, new Frame(false));
        emitALOAD(0);
        emit("getfield EVCPtr/base Ljava/lang/Object;");
        emitALOAD(0);
        emit("getfield EVCPtr/off I");
        emit("invokestatic EVC/lang/system/" + (newLine ? "printlnChars" : "printChars") + "(Ljava/lang/Object;I)V");
        emit("return");
        endMethod();
    }

    private void emitDefaultConstructor() {
        beginMethod("public <init>()V", new Frame(false));
        emit("aload_0");
        emit("invokespecial java/lang/Object/<init>()V");
        emit("return");
        endMethod();
    }

    private void emitWrapperMainIfNeeded() {
        FuncDecl selectedMain = null;
        ASTList<Decl> cur = currentProgram.FL;
        while (cur instanceof DeclList dl) {
            Decl d = dl.getHead();
            if (d instanceof FuncDecl fd && fd.I.spelling.equals("main") && fd.T instanceof IntType && fd.PL instanceof EmptyParaList && !(fd.S instanceof EmptyStmt)) {
                selectedMain = fd;
                break;
            }
            cur = dl.getNext();
        }

        if (selectedMain == null) return;

        beginMethod("public static main([Ljava/lang/String;)V", new Frame(true));
        emit("invokestatic", classname + "/main()I");
        emit("pop");
        emit("return");
        endMethod();
    }

    // ------------------------------------------------------------------
    // Program / declarations
    // ------------------------------------------------------------------

    @Override
    public Object visitProgram(Program ast, Object o) {
        generateFatPointerClass();
        emitStructClasses(ast.FL);

        emitTop(".source \"" + new File(inputFilename).getName().replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        emitTop(".class public " + classname);
        emitTop(".super java/lang/Object");
        emitTop("");
        emitTop(".field private static _scanner Ljava/util/Scanner;");

        ASTList<Decl> list = ast.FL;
        while (list instanceof DeclList dl) {
            if (dl.getHead() instanceof GlobalVarDecl gv) {
                emitTop(".field static " + gv.I.spelling + " " + fieldDescriptor(gv.T));
            }
            list = dl.getNext();
        }
        emitTop("");

        emitDefaultConstructor();
        emitBuiltinMethods();
        emitClassInitializer(ast.FL);

        ast.FL.visit(this, null);
        emitWrapperMainIfNeeded();
        return null;
    }

    private void emitClassInitializer(ASTList<Decl> decls) {
        beginMethod("static <clinit>()V", new Frame(false));
        emit("new java/util/Scanner");
        emit("dup");
        emit("getstatic java/lang/System/in Ljava/io/InputStream;");
        emit("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V");
        emit("putstatic", classname + "/_scanner Ljava/util/Scanner;");

        ASTList<Decl> cur = decls;
        while (cur instanceof DeclList dl) {
            if (dl.getHead() instanceof GlobalVarDecl gv) {
                emitGlobalInitialiser(gv);
            }
            cur = dl.getNext();
        }

        emit("return");
        endMethod();
    }

    private void emitGlobalInitialiser(GlobalVarDecl gv) {
        if (!gv.E.isEmptyExpr()) {
            if (gv.T instanceof ArrayType at && gv.E instanceof ArrayInitExpr ai) {
                emitArrayInitializer(at, ai);
            } else if (gv.T instanceof ArrayType at && at.T instanceof CharType && gv.E instanceof StringExpr se) {
                emitCharArrayFromString(at, se);
            } else {
                emitExpr(gv.E);
            }
            emit("putstatic", classname + "/" + gv.I.spelling + " " + fieldDescriptor(gv.T));
            return;
        }

        if (gv.T instanceof ArrayType at) {
            emitAllocateArrayDefault(at);
            emit("putstatic", classname + "/" + gv.I.spelling + " " + fieldDescriptor(gv.T));
        } else if (gv.T instanceof StructType st) {
            emit("new", st.I.spelling);
            emit("dup");
            emit("invokespecial", st.I.spelling + "/<init>()V");
            emit("putstatic", classname + "/" + gv.I.spelling + " " + fieldDescriptor(gv.T));
        }
    }

    private void emitStructClasses(ASTList<Decl> decls) {
        ASTList<Decl> cur = decls;
        while (cur instanceof DeclList dl) {
            if (dl.getHead() instanceof StructDefDecl sd) {
                writeStructClass(sd);
            }
            cur = dl.getNext();
        }
    }

    private void writeStructClass(StructDefDecl ast) {
        String structName = ast.S.I.spelling;
        try (PrintWriter out = new PrintWriter(new FileOutputStream(structName + ".j"))) {
            out.println(".source " + structName + ".java");
            out.println(".class public " + structName);
            out.println(".super java/lang/Object");
            out.println();

            ASTList<Decl> fields = ast.Fields;
            while (fields instanceof DeclList dl) {
                Decl field = dl.getHead();
                out.println(".field public " + field.I.spelling + " " + fieldDescriptor(field.T));
                fields = dl.getNext();
            }
            out.println();
            out.println(".method public <init>()V");
            out.println(".limit stack " + DEFAULT_STACK_LIMIT);
            out.println(".limit locals " + DEFAULT_LOCAL_LIMIT);
            out.println("\taload_0");
            out.println("\tinvokespecial java/lang/Object/<init>()V");
            fields = ast.Fields;
            while (fields instanceof DeclList dl) {
                Decl field = dl.getHead();
                if (field.T instanceof StructType st) {
                    out.println("\taload_0");
                    out.println("\tnew " + st.I.spelling);
                    out.println("\tdup");
                    out.println("\tinvokespecial " + st.I.spelling + "/<init>()V");
                    out.println("\tputfield " + structName + "/" + field.I.spelling + " " + fieldDescriptor(field.T));
                } else if (field.T instanceof ArrayType at) {
                    List<String> lines = new ArrayList<>();
                    emitStructArrayInit(lines, at);
                    out.println("\taload_0");
                    for (String line : lines) out.println("\t" + line);
                    out.println("\tputfield " + structName + "/" + field.I.spelling + " " + fieldDescriptor(field.T));
                }
                fields = dl.getNext();
            }
            out.println("\treturn");
            out.println(".end method");
        } catch (FileNotFoundException e) {
            errorReporter.reportError("Could not generate struct file", structName, ast.position);
        }
    }

    private void emitStructArrayInit(List<String> out, ArrayType at) {
        int size = declaredArraySize(at);
        if (size < 0) {
            out.add("aconst_null");
            return;
        }
        if (at.T instanceof IntType) {
            out.add(iconstLine(size));
            out.add("newarray int");
            return;
        }
        if (at.T instanceof FloatType) {
            out.add(iconstLine(size));
            out.add("newarray float");
            return;
        }
        if (at.T instanceof BooleanType) {
            out.add(iconstLine(size));
            out.add("newarray boolean");
            return;
        }
        if (at.T instanceof CharType) {
            out.add(iconstLine(size));
            out.add("newarray char");
            return;
        }

        out.add(iconstLine(size));
        out.add("anewarray " + arrayElementClass(at.T));
    }

    private String iconstLine(int value) {
        if (value == -1) return "iconst_m1";
        if (value >= 0 && value <= 5) return "iconst_" + value;
        if (value >= -128 && value <= 127) return "bipush " + value;
        if (value >= -32768 && value <= 32767) return "sipush " + value;
        return "ldc " + value;
    }

    @Override
    public Object visitDeclList(DeclList ast, Object o) {
        ast.getHead().visit(this, o);
        ast.getNext().visit(this, o);
        return null;
    }

    @Override
    public Object visitEmptyDeclList(EmptyDeclList ast, Object o) { return null; }

    @Override
    public Object visitFuncDecl(FuncDecl ast, Object o) {
        if (ast.S instanceof EmptyStmt) return null; // prototype only

        currentFunction = ast;
        Frame frame = new Frame(false);
        ASTList<Decl> params = ast.PL;
        while (params instanceof ParaList pl) {
            ((ParaDecl) pl.getHead()).index = frame.getNewIndex();
            params = pl.getNext();
        }

        beginMethod("public static " + ast.I.spelling + methodDescriptor(ast), frame);
        ast.S.visit(this, frame);

        if (ast.T instanceof VoidType) {
            emit("return");
        }
        endMethod();
        currentFunction = null;
        return null;
    }

    @Override
    public Object visitGlobalVarDecl(GlobalVarDecl ast, Object o) { return null; }

    @Override
    public Object visitLocalVarDecl(LocalVarDecl ast, Object o) {
        ast.index = currentFrame.getNewIndex();
        if (!ast.E.isEmptyExpr()) {
            if (ast.T instanceof ArrayType at && ast.E instanceof ArrayInitExpr ai) {
                emitArrayInitializer(at, ai);
            } else if (ast.T instanceof ArrayType at && at.T instanceof CharType && ast.E instanceof StringExpr se) {
                emitCharArrayFromString(at, se);
            } else {
                emitExpr(ast.E);
            }
            emitStoreSimpleVar(ast);
            return null;
        }

        if (ast.T instanceof ArrayType at) {
            emitAllocateArrayDefault(at);
            emitStoreSimpleVar(ast);
        } else if (ast.T instanceof StructType st) {
            emit("new", st.I.spelling);
            emit("dup");
            emit("invokespecial", st.I.spelling + "/<init>()V");
            emitStoreSimpleVar(ast);
        } else if (ast.T instanceof PointerType || ast.T instanceof StringType) {
            emit("aconst_null");
            emitStoreSimpleVar(ast);
        } else {
            emitDefaultValue(ast.T);
            emitStoreSimpleVar(ast);
        }
        return null;
    }

    @Override
    public Object visitStructDefDecl(StructDefDecl ast, Object o) { return null; }

    // ------------------------------------------------------------------
    // Statements
    // ------------------------------------------------------------------

    @Override
    public Object visitStmtList(StmtList ast, Object o) {
        ast.getHead().visit(this, o);
        ast.getNext().visit(this, o);
        return null;
    }

    @Override
    public Object visitEmptyStmtList(EmptyStmtList ast, Object o) { return null; }

    @Override
    public Object visitCompoundStmt(CompoundStmt ast, Object o) {
        ast.SL.visit(this, o);
        return null;
    }

    @Override
    public Object visitEmptyCompStmt(EmptyCompStmt ast, Object o) { return null; }

    @Override
    public Object visitEmptyStmt(EmptyStmt ast, Object o) { return null; }

    @Override
    public Object visitDeclStmt(DeclStmt ast, Object o) {
        ast.D.visit(this, o);
        return null;
    }

    @Override
    public Object visitExprStmt(ExprStmt ast, Object o) {
        if (!(ast.E instanceof EmptyExpr)) {
            emitExpr(ast.E);
            if (!(ast.E.type instanceof VoidType)) {
                emit("pop");
            }
        }
        return null;
    }

    @Override
    public Object visitIfStmt(IfStmt ast, Object o) {
        String falseLabel = newLabel();
        String endLabel = newLabel();
        emitExpr(ast.E);
        emit("ifeq", falseLabel);
        ast.S1.visit(this, o);
        emit("goto", endLabel);
        emitLabel(falseLabel);
        if (!(ast.S2 instanceof EmptyStmt)) ast.S2.visit(this, o);
        emitLabel(endLabel);
        return null;
    }

    @Override
    public Object visitWhileStmt(WhileStmt ast, Object o) {
        String start = newLabel();
        String end = newLabel();
        currentFrame.conStack.push(start);
        currentFrame.brkStack.push(end);
        emitLabel(start);
        emitExpr(ast.E);
        emit("ifeq", end);
        ast.S.visit(this, o);
        emit("goto", start);
        emitLabel(end);
        currentFrame.conStack.pop();
        currentFrame.brkStack.pop();
        return null;
    }

    @Override
    public Object visitForStmt(ForStmt ast, Object o) {
        String cond = newLabel();
        String update = newLabel();
        String end = newLabel();

        if (!(ast.E1 instanceof EmptyExpr)) {
            emitExpr(ast.E1);
            if (!(ast.E1.type instanceof VoidType)) emit("pop");
        }

        currentFrame.conStack.push(update);
        currentFrame.brkStack.push(end);

        emitLabel(cond);
        if (!(ast.E2 instanceof EmptyExpr)) {
            emitExpr(ast.E2);
            emit("ifeq", end);
        }
        ast.S.visit(this, o);
        emitLabel(update);
        if (!(ast.E3 instanceof EmptyExpr)) {
            emitExpr(ast.E3);
            if (!(ast.E3.type instanceof VoidType)) emit("pop");
        }
        emit("goto", cond);
        emitLabel(end);

        currentFrame.conStack.pop();
        currentFrame.brkStack.pop();
        return null;
    }

    @Override
    public Object visitBreakStmt(BreakStmt ast, Object o) {
        emit("goto", currentFrame.brkStack.peek());
        return null;
    }

    @Override
    public Object visitContinueStmt(ContinueStmt ast, Object o) {
        emit("goto", currentFrame.conStack.peek());
        return null;
    }

    @Override
    public Object visitReturnStmt(ReturnStmt ast, Object o) {
        if (ast.E instanceof EmptyExpr) {
            emit("return");
            return null;
        }

        emitExpr(ast.E);
        Type retType = expressionValueType(ast.E);
        if (retType instanceof FloatType) emit("freturn");
        else if (isReferenceType(retType)) emit("areturn");
        else emit("ireturn");
        return null;
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    @Override
    public Object visitIntExpr(IntExpr ast, Object o) { ast.IL.visit(this, o); return null; }
    @Override
    public Object visitFloatExpr(FloatExpr ast, Object o) { ast.FL.visit(this, o); return null; }
    @Override
    public Object visitBooleanExpr(BooleanExpr ast, Object o) { ast.BL.visit(this, o); return null; }
    @Override
    public Object visitStringExpr(StringExpr ast, Object o) { ast.SL.visit(this, o); return null; }
    @Override
    public Object visitNewExpr(NewExpr ast, Object o) {
        emit("new EVCPtr");
        emit("dup");
        emit("new", ast.I.spelling);
        emit("dup");
        emit("invokespecial", ast.I.spelling + "/<init>()V");
        emit("iconst_0");
        emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
        return null;
    }
    @Override
    public Object visitNullExpr(NullExpr ast, Object o) {
        emit("new EVCPtr");
        emit("dup");
        emit("aconst_null");
        emit("iconst_0");
        emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
        return null;
    }
    @Override
    public Object visitCharExpr(CharExpr ast, Object o) { ast.CL.visit(this, o); return null; }
    @Override
    public Object visitEmptyExpr(EmptyExpr ast, Object o) { return null; }

    @Override
    public Object visitVarExpr(VarExpr ast, Object o) {
        ast.V.visit(this, o);
        return null;
    }

    @Override
    public Object visitSimpleVar(SimpleVar ast, Object o) {
        emitLoadSimpleVar((Decl) ast.I.decl);
        return null;
    }

    @Override
    public Object visitAssignExpr(AssignExpr ast, Object o) {
        if (isCharArrayType(ast.E1.type) && isStringLike(ast.E2.type) &&
                (ast.E1 instanceof VarExpr || ast.E1 instanceof MemberExpr || ast.E1 instanceof ArrowExpr)) {
            emitExpr(ast.E1);
            emitASTORE(TMP_OBJ0);
            emitALOAD(TMP_OBJ0);
            if (ast.E2.type instanceof StringType) {
                ast.E2.visit(this, currentFrame);
                emit("invokestatic EVC/lang/system/copyString([CLjava/lang/String;)I");
            } else {
                emitExpr(ast.E2);
                emit("invokestatic EVC/lang/system/copyChars([C[C)I");
            }
            emit("pop");
            emitALOAD(TMP_OBJ0);
            return null;
        }

        Type valueType = expressionValueType(ast.E2);
        emitExpr(ast.E2);
        emitStoreExprValueToTemp(valueType);

        if (ast.E1 instanceof VarExpr ve && ve.V instanceof SimpleVar sv) {
            emitReloadExprValueFromTemp(valueType);
            emitStoreSimpleVar((Decl) sv.I.decl);
        } else if (ast.E1 instanceof ArrayExpr ae) {
            if (ae.V.type instanceof PointerType) {
                emitPointerBaseAndOffsetWithIndex(ae.V, ae.E);
                emitStorePointerValue(ae.type);
            } else {
                emitExpr(ae.V);
                emitExpr(ae.E);
                emitReloadExprValueFromTemp(valueType);
                emitStoreToArrayElement(ae.type);
            }
        } else if (ast.E1 instanceof MemberExpr me) {
            emitExpr(me.StructExpr);
            emitReloadExprValueFromTemp(valueType);
            StructType owner = (StructType) me.StructExpr.type;
            emitPutField(owner.I.spelling, me.Field.spelling, me.type);
        } else if (ast.E1 instanceof ArrowExpr ae) {
            emitPointerBaseAndOffset(ae.PtrExpr);
            StructType owner = (StructType) ((PointerType) ae.PtrExpr.type).T;
            String arrayDesc = "[L" + owner.I.spelling + ";";
            String useArray = newLabel();
            String done = newLabel();
            emitALOAD(TMP_OBJ0);
            emit("instanceof", arrayDesc);
            emit("ifne", useArray);
            emitALOAD(TMP_OBJ0);
            emit("checkcast", owner.I.spelling);
            emitReloadExprValueFromTemp(valueType);
            emitPutField(owner.I.spelling, ae.Field.spelling, ae.type);
            emit("goto", done);
            emitLabel(useArray);
            emitALOAD(TMP_OBJ0);
            emit("checkcast", arrayDesc);
            emitILOAD(TMP_INT0);
            emit("aaload");
            emitReloadExprValueFromTemp(valueType);
            emitPutField(owner.I.spelling, ae.Field.spelling, ae.type);
            emitLabel(done);
        } else if (ast.E1 instanceof UnaryExpr ue && ue.O.spelling.equals("*")) {
            emitPointerBaseAndOffset(ue.E);
            emitStorePointerValue(ue.type);
        }

        emitReloadExprValueFromTemp(valueType);
        return null;
    }

    @Override
    public Object visitBinaryExpr(BinaryExpr ast, Object o) {
        String op = ast.O.spelling;

        if (op.equals("i&&")) {
            String falseLabel = newLabel();
            String endLabel = newLabel();
            emitExpr(ast.E1);
            emit("ifeq", falseLabel);
            emitExpr(ast.E2);
            emit("ifeq", falseLabel);
            emit("iconst_1");
            emit("goto", endLabel);
            emitLabel(falseLabel);
            emit("iconst_0");
            emitLabel(endLabel);
            return null;
        }

        if (op.equals("i||")) {
            String trueLabel = newLabel();
            String endLabel = newLabel();
            emitExpr(ast.E1);
            emit("ifne", trueLabel);
            emitExpr(ast.E2);
            emit("ifne", trueLabel);
            emit("iconst_0");
            emit("goto", endLabel);
            emitLabel(trueLabel);
            emit("iconst_1");
            emitLabel(endLabel);
            return null;
        }

        if (op.equals("p+") || op.equals("p-")) {
            emitPointerBaseAndOffset(ast.E1.type instanceof PointerType ? ast.E1 : ast.E2);
            if (ast.E1.type instanceof PointerType) {
                emitExpr(ast.E2);
            } else {
                emitExpr(ast.E1);
            }
            emitISTORE(TMP_INT1);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emitILOAD(TMP_INT0);
            emitILOAD(TMP_INT1);
            emit(op.equals("p+") ? "iadd" : "isub");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            return null;
        }

        if (op.equals("pp-")) {
            emitPointerBaseAndOffset(ast.E1);
            emitILOAD(TMP_INT0);
            emitISTORE(TMP_INT1);
            emitPointerBaseAndOffset(ast.E2);
            emitILOAD(TMP_INT1);
            emitILOAD(TMP_INT0);
            emit("isub");
            return null;
        }

        if (op.equals("p==")) {
            String trueLabel = newLabel();
            String endLabel = newLabel();
            String baseFail = endLabel + "_basefail";
            emitPointerBaseAndOffset(ast.E1);
            emitALOAD(TMP_OBJ0); emitASTORE(TMP_OBJ1);
            emitILOAD(TMP_INT0); emitISTORE(TMP_INT1);
            emitPointerBaseAndOffset(ast.E2);
            emitALOAD(TMP_OBJ1);
            emitALOAD(TMP_OBJ0);
            emit("if_acmpne", baseFail);
            emitILOAD(TMP_INT1);
            emitILOAD(TMP_INT0);
            emit("if_icmpeq", trueLabel);
            emitLabel(baseFail);
            emit("iconst_0");
            emit("goto", endLabel);
            emitLabel(trueLabel);
            emit("iconst_1");
            emitLabel(endLabel);
            return null;
        }

        if (op.equals("p!=")) {
            String trueLabel = newLabel();
            String endLabel = newLabel();
            String checkOffset = endLabel + "_checkoff";
            emitPointerBaseAndOffset(ast.E1);
            emitALOAD(TMP_OBJ0); emitASTORE(TMP_OBJ1);
            emitILOAD(TMP_INT0); emitISTORE(TMP_INT1);
            emitPointerBaseAndOffset(ast.E2);
            emitALOAD(TMP_OBJ1);
            emitALOAD(TMP_OBJ0);
            emit("if_acmpne", trueLabel);
            emitILOAD(TMP_INT1);
            emitILOAD(TMP_INT0);
            emit("if_icmpne", trueLabel);
            emit("iconst_0");
            emit("goto", endLabel);
            emitLabel(trueLabel);
            emit("iconst_1");
            emitLabel(endLabel);
            return null;
        }

        emitExpr(ast.E1);
        emitExpr(ast.E2);

        switch (op) {
            case "i+" -> emit("iadd");
            case "i-" -> emit("isub");
            case "i*" -> emit("imul");
            case "i/" -> emit("idiv");
            case "i%" -> emit("irem");
            case "i<<" -> emit("ishl");
            case "i>>" -> emit("ishr");
            case "i&" -> emit("iand");
            case "i|" -> emit("ior");
            case "i^" -> emit("ixor");
            case "f+" -> emit("fadd");
            case "f-" -> emit("fsub");
            case "f*" -> emit("fmul");
            case "f/" -> emit("fdiv");
            case "i==", "i!=", "i<", "i<=", "i>", "i>=" -> emitIntComparison(op);
            case "f==", "f!=", "f<", "f<=", "f>", "f>=" -> emitFloatComparison(op);
            default -> { }
        }
        return null;
    }

    private void emitIntComparison(String op) {
        String trueLabel = newLabel();
        String endLabel = newLabel();
        String branch = switch (op) {
            case "i==" -> "if_icmpeq";
            case "i!=" -> "if_icmpne";
            case "i<" -> "if_icmplt";
            case "i<=" -> "if_icmple";
            case "i>" -> "if_icmpgt";
            case "i>=" -> "if_icmpge";
            default -> throw new IllegalStateException(op);
        };
        emit(branch, trueLabel);
        emit("iconst_0");
        emit("goto", endLabel);
        emitLabel(trueLabel);
        emit("iconst_1");
        emitLabel(endLabel);
    }

    private void emitFloatComparison(String op) {
        String trueLabel = newLabel();
        String endLabel = newLabel();
        emit("fcmpg");
        String branch = switch (op) {
            case "f==" -> "ifeq";
            case "f!=" -> "ifne";
            case "f<" -> "iflt";
            case "f<=" -> "ifle";
            case "f>" -> "ifgt";
            case "f>=" -> "ifge";
            default -> throw new IllegalStateException(op);
        };
        emit(branch, trueLabel);
        emit("iconst_0");
        emit("goto", endLabel);
        emitLabel(trueLabel);
        emit("iconst_1");
        emitLabel(endLabel);
    }

    @Override
    public Object visitUnaryExpr(UnaryExpr ast, Object o) {
        String op = ast.O.spelling;

        switch (op) {
            case "i+", "+" -> emitExpr(ast.E);
            case "f+" -> emitExpr(ast.E);
            case "i-" -> { emitExpr(ast.E); emit("ineg"); }
            case "f-" -> { emitExpr(ast.E); emit("fneg"); }
            case "i!" -> {
                String trueLabel = newLabel();
                String endLabel = newLabel();
                emitExpr(ast.E);
                emit("ifeq", trueLabel);
                emit("iconst_0");
                emit("goto", endLabel);
                emitLabel(trueLabel);
                emit("iconst_1");
                emitLabel(endLabel);
            }
            case "i~" -> { emitExpr(ast.E); emit("iconst_m1"); emit("ixor"); }
            case "&" -> emitAddressOf(ast.E);
            case "*" -> {
                emitPointerBaseAndOffset(ast.E);
                emitLoadPointerValue(ast.type);
            }
            case "i++", "i--", "f++", "f--", "p++", "p--" -> emitPrefixIncDec(ast, false);
            default -> emitExpr(ast.E);
        }
        return null;
    }

    @Override
    public Object visitPostfixExpr(PostfixExpr ast, Object o) {
        emitPrefixIncDec(new UnaryExpr(new Operator(ast.O.spelling, ast.O.position), ast.E, ast.position), true);
        return null;
    }

    private void emitPrefixIncDec(Expr unaryOrPostfix, boolean postfix) {
        Expr target;
        String op;
        Type t;
        if (unaryOrPostfix instanceof UnaryExpr ue) {
            target = ue.E;
            op = ue.O.spelling;
            t = ue.type;
        } else {
            PostfixExpr pe = (PostfixExpr) unaryOrPostfix;
            target = pe.E;
            op = pe.O.spelling;
            t = pe.type;
        }

        emitExpr(target);
        if (t instanceof FloatType) emitFSTORE(TMP_FLOAT0);
        else if (isReferenceType(t)) emitASTORE(TMP_VALA);
        else emitISTORE(TMP_VALI);

        if (postfix) {
            if (t instanceof FloatType) {
                emitFLOAD(TMP_FLOAT0);
                emitFSTORE(TMP_OLDF);
            } else if (isReferenceType(t)) {
                emitALOAD(TMP_VALA);
                emitASTORE(TMP_OLDA);
            } else {
                emitILOAD(TMP_VALI);
                emitISTORE(TMP_OLDI);
            }
        }

        if (t instanceof FloatType) {
            emitFLOAD(TMP_FLOAT0);
            emit("fconst_1");
            emit(op.contains("++") ? "fadd" : "fsub");
            emitFSTORE(TMP_FLOAT0);
        } else if (t instanceof PointerType) {
            emitALOAD(TMP_VALA);
            emitASTORE(TMP_PTR0);
            emitALOAD(TMP_PTR0);
            emit("getfield EVCPtr/base Ljava/lang/Object;");
            emitASTORE(TMP_OBJ0);
            emitALOAD(TMP_PTR0);
            emit("getfield EVCPtr/off I");
            emitISTORE(TMP_INT0);
            emit("new EVCPtr");
            emit("dup");
            emitALOAD(TMP_OBJ0);
            emitILOAD(TMP_INT0);
            emit("iconst_1");
            emit(op.contains("++") ? "iadd" : "isub");
            emit("invokespecial EVCPtr/<init>(Ljava/lang/Object;I)V");
            emitASTORE(TMP_VALA);
        } else {
            emitILOAD(TMP_VALI);
            emit("iconst_1");
            emit(op.contains("++") ? "iadd" : "isub");
            emitISTORE(TMP_VALI);
        }

        if (target instanceof VarExpr ve && ve.V instanceof SimpleVar sv) {
            emitReloadExprValueFromTemp(t);
            emitStoreSimpleVar((Decl) sv.I.decl);
        } else if (target instanceof ArrayExpr ae) {
            if (ae.V.type instanceof PointerType) {
                emitPointerBaseAndOffsetWithIndex(ae.V, ae.E);
                emitStorePointerValue(ae.type);
            } else {
                emitExpr(ae.V);
                emitExpr(ae.E);
                emitReloadExprValueFromTemp(t);
                emitStoreToArrayElement(ae.type);
            }
        } else if (target instanceof UnaryExpr ue && ue.O.spelling.equals("*")) {
            emitPointerBaseAndOffset(ue.E);
            emitStorePointerValue(ue.type);
        } else if (target instanceof MemberExpr me) {
            emitExpr(me.StructExpr);
            emitReloadExprValueFromTemp(t);
            StructType owner = (StructType) me.StructExpr.type;
            emitPutField(owner.I.spelling, me.Field.spelling, me.type);
        } else if (target instanceof ArrowExpr ae) {
            emitPointerBaseAndOffset(ae.PtrExpr);
            StructType owner = (StructType) ((PointerType) ae.PtrExpr.type).T;
            String arrayDesc = "[L" + owner.I.spelling + ";";
            String useArray = newLabel();
            String done = newLabel();
            emitALOAD(TMP_OBJ0);
            emit("instanceof", arrayDesc);
            emit("ifne", useArray);
            emitALOAD(TMP_OBJ0);
            emit("checkcast", owner.I.spelling);
            emitReloadExprValueFromTemp(t);
            emitPutField(owner.I.spelling, ae.Field.spelling, ae.type);
            emit("goto", done);
            emitLabel(useArray);
            emitALOAD(TMP_OBJ0);
            emit("checkcast", arrayDesc);
            emitILOAD(TMP_INT0);
            emit("aaload");
            emitReloadExprValueFromTemp(t);
            emitPutField(owner.I.spelling, ae.Field.spelling, ae.type);
            emitLabel(done);
        }

        if (postfix) {
            if (t instanceof FloatType) emitFLOAD(TMP_OLDF);
            else if (isReferenceType(t)) emitALOAD(TMP_OLDA);
            else emitILOAD(TMP_OLDI);
        } else {
            emitReloadExprValueFromTemp(t);
        }
    }

    @Override
    public Object visitCallExpr(CallExpr ast, Object o) {
        FuncDecl callee = null;
        if (ast.E instanceof VarExpr ve && ve.V instanceof SimpleVar sv && sv.I.decl instanceof FuncDecl fd) {
            callee = fd;
        }
        if (callee == null) return null;

        ASTList<Arg> args = ast.AL;
        while (args instanceof ArgList al) {
            emitExpr(al.getHead().E);
            args = al.getNext();
        }
        emit("invokestatic", classname + "/" + callee.I.spelling + methodDescriptor(callee));
        return null;
    }

    @Override
    public Object visitArrayExpr(ArrayExpr ast, Object o) {
        if (ast.V.type instanceof PointerType) {
            emitPointerBaseAndOffsetWithIndex(ast.V, ast.E);
            emitLoadPointerValue(ast.type);
        } else {
            emitExpr(ast.V);
            emitExpr(ast.E);
            emitLoadFromArrayElement(ast.type);
        }
        return null;
    }

    @Override
    public Object visitArrayInitExpr(ArrayInitExpr ast, Object o) { return null; }
    @Override
    public Object visitArrayExprList(ArrayExprList ast, Object o) { return null; }

    @Override
    public Object visitEmptyArrayExprList(EmptyArrayExprList ast, Object o) { return null; }

    @Override
    public Object visitMemberExpr(MemberExpr ast, Object o) {
        emitExpr(ast.StructExpr);
        StructType owner = (StructType) ast.StructExpr.type;
        emitGetField(owner.I.spelling, ast.Field.spelling, ast.type);
        return null;
    }

    @Override
    public Object visitArrowExpr(ArrowExpr ast, Object o) {
        emitPointerBaseAndOffset(ast.PtrExpr);
        StructType owner = (StructType) ((PointerType) ast.PtrExpr.type).T;
        String arrayDesc = "[L" + owner.I.spelling + ";";
        String useArray = newLabel();
        String done = newLabel();
        emitALOAD(TMP_OBJ0);
        emit("instanceof", arrayDesc);
        emit("ifne", useArray);
        emitALOAD(TMP_OBJ0);
        emit("checkcast", owner.I.spelling);
        emitGetField(owner.I.spelling, ast.Field.spelling, ast.type);
        emit("goto", done);
        emitLabel(useArray);
        emitALOAD(TMP_OBJ0);
        emit("checkcast", arrayDesc);
        emitILOAD(TMP_INT0);
        emit("aaload");
        emitGetField(owner.I.spelling, ast.Field.spelling, ast.type);
        emitLabel(done);
        return null;
    }

    // ------------------------------------------------------------------
    // Literals / leaf nodes
    // ------------------------------------------------------------------

    @Override
    public Object visitIntLiteral(IntLiteral ast, Object o) { emitICONST(Integer.parseInt(ast.spelling)); return null; }
    @Override
    public Object visitFloatLiteral(FloatLiteral ast, Object o) { emitFCONST(Float.parseFloat(ast.spelling)); return null; }
    @Override
    public Object visitBooleanLiteral(BooleanLiteral ast, Object o) { emit(ast.spelling.equals("true") ? "iconst_1" : "iconst_0"); return null; }
    @Override
    public Object visitStringLiteral(StringLiteral ast, Object o) { emitStringConstant(ast.spelling); return null; }
    @Override
    public Object visitCharLiteral(CharLiteral ast, Object o) { emitICONST(decodeCharLiteral(ast)); return null; }
    @Override
    public Object visitIdent(Ident ast, Object o) { return null; }
    @Override
    public Object visitOperator(Operator ast, Object o) { return null; }
    @Override
    public Object visitParaList(ParaList ast, Object o) { return null; }
    @Override
    public Object visitParaDecl(ParaDecl ast, Object o) { return null; }
    @Override
    public Object visitArgList(ArgList ast, Object o) { return null; }
    @Override
    public Object visitArg(Arg ast, Object o) { return null; }
    @Override
    public Object visitEmptyArgList(EmptyArgList ast, Object o) { return null; }
    @Override
    public Object visitEmptyParaList(EmptyParaList ast, Object o) { return null; }
    @Override
    public Object visitVoidType(VoidType ast, Object o) { return null; }
    @Override
    public Object visitBooleanType(BooleanType ast, Object o) { return null; }
    @Override
    public Object visitIntType(IntType ast, Object o) { return null; }
    @Override
    public Object visitFloatType(FloatType ast, Object o) { return null; }
    @Override
    public Object visitStringType(StringType ast, Object o) { return null; }
    @Override
    public Object visitArrayType(ArrayType ast, Object o) { return null; }
    @Override
    public Object visitErrorType(ErrorType ast, Object o) { return null; }
    @Override
    public Object visitCharType(CharType ast, Object o) { return null; }
    @Override
    public Object visitPointerType(PointerType ast, Object o) { return null; }
    @Override
    public Object visitStructType(StructType ast, Object o) { return null; }

    private void generateFatPointerClass() {
        try (PrintWriter writer = new PrintWriter(new FileOutputStream("EVCPtr.j"))) {
            writer.println(".source EVCPtr.java");
            writer.println(".class public EVCPtr");
            writer.println(".super java/lang/Object");
            writer.println(".field public base Ljava/lang/Object;");
            writer.println(".field public off I");
            writer.println();
            writer.println(".method public <init>(Ljava/lang/Object;I)V");
            writer.println(".limit stack 4");
            writer.println(".limit locals 3");
            writer.println("\taload_0");
            writer.println("\tinvokespecial java/lang/Object/<init>()V");
            writer.println("\taload_0");
            writer.println("\taload_1");
            writer.println("\tputfield EVCPtr/base Ljava/lang/Object;");
            writer.println("\taload_0");
            writer.println("\tiload_2");
            writer.println("\tputfield EVCPtr/off I");
            writer.println("\treturn");
            writer.println(".end method");
        } catch (FileNotFoundException e) {
            errorReporter.reportError("Could not generate EVCPtr.j", "", new SourcePosition());
        }
    }
}
