package art.arcane.volmlib.util.scheduling;

public abstract class ARBase implements Runnable {
    private int id;

    protected ARBase() {
        this(0);
    }

    protected ARBase(int interval) {
        id = schedule(interval);
    }

    protected abstract int schedule(int interval);

    protected abstract void cancelSchedule(int taskId);

    public void cancel() {
        cancelSchedule(id);
    }

    public int getId() {
        return id;
    }
}
