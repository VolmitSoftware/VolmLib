package art.arcane.volmlib.util.scheduling;

final class CancellableDelayedTask implements SchedulerUtils.TaskHandle, Runnable {
    private Runnable action;
    private Runnable retired;
    private Cancellation cancellation;
    private boolean cancelRequested;
    private State state = State.PENDING;

    CancellableDelayedTask(FoliaScheduler.DelayedTask task) {
        action = task.action();
        retired = task.retired();
    }

    void attach(Cancellation nativeCancellation) {
        boolean cancel;
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            cancellation = nativeCancellation;
            cancel = cancelRequested;
        }
        if (cancel) {
            cancel();
        }
    }

    @Override
    public void run() {
        Runnable callback;
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            state = cancelRequested ? State.CANCELLED : State.RUNNING;
            callback = cancelRequested ? retired : action;
            action = null;
            retired = null;
        }
        try {
            callback.run();
        } finally {
            synchronized (this) {
                if (state == State.RUNNING) {
                    state = State.FINISHED;
                }
                cancellation = null;
            }
        }
    }

    void retire() {
        Runnable callback;
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            state = State.CANCELLED;
            callback = retired;
            action = null;
            retired = null;
            cancellation = null;
        }
        callback.run();
    }

    @Override
    public void cancel() {
        Cancellation nativeCancellation;
        synchronized (this) {
            if (state != State.PENDING) {
                return;
            }
            cancelRequested = true;
            nativeCancellation = cancellation;
        }
        if (nativeCancellation != null && nativeCancellation.cancel()) {
            retire();
        }
    }

    @Override
    public synchronized boolean isCancelled() {
        return state == State.CANCELLED;
    }

    @FunctionalInterface
    interface Cancellation {
        boolean cancel();
    }

    private enum State {
        PENDING, RUNNING, FINISHED, CANCELLED
    }
}
