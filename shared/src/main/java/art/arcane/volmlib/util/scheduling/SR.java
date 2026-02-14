package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.plugin.CancellableTask;

public abstract class SR extends SRBase implements Runnable, CancellableTask {
    public SR() {
        super();
    }

    public SR(int interval) {
        super(interval);
    }

    @Override
    protected int schedule(int interval) {
        return SchedulerBridge.scheduleSyncRepeating(this, interval);
    }

    @Override
    protected void cancelSchedule(int taskId) {
        SchedulerBridge.cancel(taskId);
    }
}
