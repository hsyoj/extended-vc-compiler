package EVC;

import EVC.ASTs.*;

public final class StdEnvironment {

    public static Type booleanType, intType, floatType, stringType, voidType, errorType, charType;

    public static Decl printIntDecl;
    public static Decl printlnIntDecl;
    public static Decl printFloatDecl;
    public static Decl printlnFloatDecl;
    public static Decl printStringDecl;
    public static Decl printlnStringDecl;
    public static Decl printBooleanDecl;
    public static Decl printlnBooleanDecl;
    public static Decl printCharDecl;
    public static Decl printlnCharDecl;
    public static Decl printCharArrayDecl;
    public static Decl printlnCharArrayDecl;
    public static Decl printlnDecl;

    public static Decl getIntDecl;
    public static Decl getIntPromptDecl;
    public static Decl getIntPromptCharArrayDecl;
    public static Decl getIntPromptCharPointerDecl;
    public static Decl getFloatDecl;
    public static Decl getFloatPromptDecl;
    public static Decl getCharDecl;
    public static Decl getCharPromptDecl;
    public static Decl getStringDecl;
    public static Decl getStringPromptDecl;
    public static Decl readStringDecl;
    public static Decl readStringPromptDecl;
    public static Decl ordDecl;
    public static Decl chrDecl;
}
