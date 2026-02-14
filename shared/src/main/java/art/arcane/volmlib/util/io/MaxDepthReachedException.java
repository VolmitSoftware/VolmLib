package art.arcane.volmlib.util.io;

@SuppressWarnings("serial")
public class MaxDepthReachedException extends RuntimeException {
    public MaxDepthReachedException(String msg) {
        super(msg);
    }
}
