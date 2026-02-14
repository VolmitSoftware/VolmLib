package art.arcane.volmlib.util.scheduling;

public abstract class SRBase implements Runnable {
    private int id;

    protected SRBase() {
        this(0);
    }

    protected SRBase(int interval) {
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
