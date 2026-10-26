package EVC.Scanner;

public class SourcePosition {

    public int lineStart, lineFinish;
    public int charStart, charFinish;

    public SourcePosition() {
        this(0, 0, 0, 0);
    }

    public SourcePosition(int lineStart, int lineFinish) {
        this(lineStart, lineFinish, 0, 0);
    }

    public SourcePosition(int lineStart, int lineFinish, int charStart, int charFinish) {
        this.lineStart = lineStart;
        this.lineFinish = lineFinish;
        this.charStart = charStart;
        this.charFinish = charFinish;
    }

    @Override
    public String toString() {
        return String.format("%d(%d)..%d(%d)", lineStart, charStart, lineFinish, charFinish);
    }
}

