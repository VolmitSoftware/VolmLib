package art.arcane.volmlib.util.director.runtime;

public final class DirectorExecutionResult {
    private final boolean handled;
    private final boolean success;
    private final String message;

    private DirectorExecutionResult(boolean handled, boolean success, String message) {
        this.handled = handled;
        this.success = success;
        this.message = message;
    }

    public static DirectorExecutionResult notHandled() {
        return new DirectorExecutionResult(false, false, null);
    }

    public static DirectorExecutionResult success() {
        return new DirectorExecutionResult(true, true, null);
    }

    public static DirectorExecutionResult failure(String message) {
        return new DirectorExecutionResult(true, false, message);
    }

    public boolean isHandled() {
        return handled;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
