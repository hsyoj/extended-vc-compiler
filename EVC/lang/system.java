package EVC.lang;

import java.util.Scanner;

public final class system {

    private static final Scanner INPUT = new Scanner(java.lang.System.in);

    private system() {}

    public static int getInt() {
        return INPUT.nextInt();
    }

    public static int getInt(String prompt) {
        printPrompt(prompt);
        return getInt();
    }

    public static int getInt(char[] prompt) {
        print(prompt);
        flush();
        return getInt();
    }

    public static int getInt(Object base, int offset) {
        printChars(base, offset);
        flush();
        return getInt();
    }

    public static float getFloat() {
        return INPUT.nextFloat();
    }

    public static float getFloat(String prompt) {
        printPrompt(prompt);
        return getFloat();
    }

    public static char getChar() {
        String token = INPUT.next();
        return token.isEmpty() ? '\0' : token.charAt(0);
    }

    public static char getChar(String prompt) {
        printPrompt(prompt);
        return getChar();
    }

    public static String getString() {
        return INPUT.next();
    }

    public static String getString(String prompt) {
        printPrompt(prompt);
        return getString();
    }

    public static int getString(char[] buffer) {
        return copyString(buffer, INPUT.next());
    }

    public static int getString(char[] buffer, String prompt) {
        printPrompt(prompt);
        return getString(buffer);
    }

    public static int getString(Object base, int offset) {
        char[] dest = requireCharArray(base);
        if (dest.length == 0 || offset < 0 || offset >= dest.length) {
            return 0;
        }
        String src = INPUT.next();
        int limit = Math.max(dest.length - offset - 1, 0);
        int n = Math.min(src.length(), limit);
        for (int i = 0; i < n; i++) {
            dest[offset + i] = src.charAt(i);
        }
        dest[offset + n] = '\0';
        for (int i = offset + n + 1; i < dest.length; i++) {
            dest[i] = '\0';
        }
        return n;
    }

    public static int getString(Object base, int offset, String prompt) {
        printPrompt(prompt);
        return getString(base, offset);
    }

    public static int ord(char ch) {
        return ch;
    }

    public static char chr(int code) {
        return (char) code;
    }

    public static char[] toCharArray(String value) {
        if (value == null) {
            return new char[] { '\0' };
        }
        char[] out = new char[value.length() + 1];
        for (int i = 0; i < value.length(); i++) {
            out[i] = value.charAt(i);
        }
        out[value.length()] = '\0';
        return out;
    }

    public static int copyString(char[] dest, String src) {
        if (dest == null || dest.length == 0) {
            return 0;
        }
        if (src == null) {
            dest[0] = '\0';
            return 0;
        }
        int limit = Math.max(dest.length - 1, 0);
        int n = Math.min(src.length(), limit);
        for (int i = 0; i < n; i++) {
            dest[i] = src.charAt(i);
        }
        dest[n] = '\0';
        for (int i = n + 1; i < dest.length; i++) {
            dest[i] = '\0';
        }
        return n;
    }

    public static int copyChars(char[] dest, char[] src) {
        if (dest == null || dest.length == 0) {
            return 0;
        }
        if (src == null) {
            dest[0] = '\0';
            return 0;
        }
        int limit = Math.max(dest.length - 1, 0);
        int n = 0;
        while (n < limit && n < src.length && src[n] != '\0') {
            dest[n] = src[n];
            n++;
        }
        dest[n] = '\0';
        for (int i = n + 1; i < dest.length; i++) {
            dest[i] = '\0';
        }
        return n;
    }

    public static int strlen(char[] value) {
        return strlen(value, 0);
    }

    public static int strlen(char[] value, int offset) {
        if (value == null || offset < 0 || offset >= value.length) return 0;
        int n = offset;
        while (n < value.length && value[n] != '\0') {
            n++;
        }
        return n - offset;
    }

    public static String fromCharArray(char[] value) {
        return fromCharArray(value, 0);
    }

    public static String fromCharArray(char[] value, int offset) {
        if (value == null || offset < 0 || offset >= value.length) {
            return "";
        }
        return new String(value, offset, strlen(value, offset));
    }

    public static void print(int value) { java.lang.System.out.print(value); }
    public static void println(int value) { java.lang.System.out.println(value); }
    public static void print(float value) { java.lang.System.out.print(value); }
    public static void println(float value) { java.lang.System.out.println(value); }
    public static void print(boolean value) { java.lang.System.out.print(value); }
    public static void println(boolean value) { java.lang.System.out.println(value); }
    public static void print(char value) { java.lang.System.out.print(value); }
    public static void println(char value) { java.lang.System.out.println(value); }
    public static void print(String value) { java.lang.System.out.print(value); }
    public static void println(String value) { java.lang.System.out.println(value); }
    public static void print(char[] value) { java.lang.System.out.print(fromCharArray(value)); }
    public static void println(char[] value) { java.lang.System.out.println(fromCharArray(value)); }
    public static void printChars(Object base, int offset) { java.lang.System.out.print(fromCharArray(requireCharArray(base), offset)); }
    public static void printlnChars(Object base, int offset) { java.lang.System.out.println(fromCharArray(requireCharArray(base), offset)); }
    public static void println() { java.lang.System.out.println(); }
    public static void flush() { java.lang.System.out.flush(); }

    private static void printPrompt(String prompt) {
        if (prompt != null) {
            java.lang.System.out.print(prompt);
            java.lang.System.out.flush();
        }
    }

    private static char[] requireCharArray(Object base) {
        if (base instanceof char[] arr) {
            return arr;
        }
        throw new IllegalArgumentException("expected char[] base for string/pointer operation");
    }
}
