package www.ontologyutils.toolbox;

/**
 * We throw this exception if we observe that the thread has been interrupted.
 * This allows terminating the repair operations and is used in the protege
 * plugin.
 */
public class CanceledException extends RuntimeException {
    /**
     * Create a new canceled exception.
     */
    public CanceledException() {
        super("The operation was canceled.");
    }
}
