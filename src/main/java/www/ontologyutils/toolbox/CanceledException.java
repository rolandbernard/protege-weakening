package www.ontologyutils.toolbox;

public class CanceledException extends RuntimeException {
    public CanceledException() {
        super("The operation was canceled.");
    }
}
