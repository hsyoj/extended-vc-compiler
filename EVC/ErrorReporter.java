/*
 * ErrorReporter.java     
 */

package EVC;

import EVC.Scanner.SourcePosition;

public class ErrorReporter {

    private int numErrors;

    public ErrorReporter() {
        numErrors = 0;
    }

    /**
     * Reports an error message with the specified details.
     * 
     * @param message The error message template
     * @param tokenName The token causing the error
     * @param pos The position of the error in the source file
     */
    public void reportError(String message, String tokenName, SourcePosition pos) {
 	System.out.printf("ERROR: %d(%d)..%d(%d): ",
    		pos.lineStart, pos.charStart, pos.lineFinish, pos.charFinish);

	if (message.indexOf('%') < 0) {
    	    System.out.print(message);
    	    if (!tokenName.isEmpty()) {
        	System.out.print(": " + tokenName);
    	    }
	} else {
    	    for (int i = 0; i < message.length(); i++) {
        	char ch = message.charAt(i);
        	if (ch == '%') {
            	     System.out.print(tokenName);
        	} else {
                     System.out.print(ch);
        	}
    	    }
   	}

       System.out.println();
       numErrors++;
    }

    /**
     * Reports a restriction message.
     * 
     * @param message The restriction message
     */
    public void reportRestriction(String message) {
        System.out.println("RESTRICTION: " + message);
    }

    /**
     * Gets the total number of errors reported.
     * 
     * @return The number of errors
     */
    public int getNumErrors() {
        return numErrors;
    }
}
