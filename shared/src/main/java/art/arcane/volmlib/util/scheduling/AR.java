package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.plugin.CancellableTask;

public abstract class AR extends ARBase implements Runnable, CancellableTask {
    public AR() {
        super();
    }

    public AR(int interval) {
        super(interval);
    }

    @Override
    protected int schedule(int interval) {
        return SchedulerBridge.scheduleAsyncRepeating(this, interval);
    }

    @Override
    protected void cancelSchedule(int taskId) {
        SchedulerBridge.cancel(taskId);
    }
}
