package EVC;

import EVC.ASTs.AST;
import EVC.Checker.Checker;
import EVC.CodeGen.Emitter;
import EVC.Parser.Parser;
import EVC.Scanner.Scanner;
import EVC.Scanner.SourceFile;

public class evc {

    public static void main(String[] args) {
        System.out.println("======= The EVC Compiler =======\n");

        if (args.length != 1) {
            System.out.println("[# evc #]: Usage: java EVC.evc <filename>");
            System.out.println("[# evc #]: Example: java EVC.evc test.evc");
            return;
        }

        String inputFilename = args[0];
        ErrorReporter reporter = new ErrorReporter();

        try {
            SourceFile source = new SourceFile(inputFilename);
            System.out.println("Pass 1: Lexical and Syntactic Analysis");
            Scanner scanner = new Scanner(source, reporter);
            Parser parser = new Parser(scanner, reporter);
            AST theAST = parser.parseProgram();

            if (reporter.getNumErrors() > 0 || theAST == null) {
                System.out.println("Compilation was unsuccessful.");
                return;
            }

            System.out.println("Pass 2: Semantic Analysis");
            Checker checker = new Checker(reporter);
            checker.check(theAST);

            if (reporter.getNumErrors() > 0) {
                System.out.println("Compilation was unsuccessful.");
                return;
            }

            System.out.println("Pass 3: Jasmin Code Generation");
            Emitter emitter = new Emitter(inputFilename, reporter);
            emitter.gen(theAST);

            if (reporter.getNumErrors() == 0) {
                System.out.println("Compilation was successful.");
            } else {
                System.out.println("Compilation was unsuccessful.");
            }
        } catch (Exception e) {
            System.out.println("[# evc #]: Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
