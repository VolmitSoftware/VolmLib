package art.arcane.volmlib.util.scheduling;

public abstract class S extends SBase implements Runnable {
    public S() {
        super();
    }

    public S(int delay) {
        super(delay);
    }

    @Override
    protected void schedule(int delay) {
        SchedulerBridge.scheduleSync(this, delay);
    }
}
