package art.arcane.volmlib.util.scheduling;

public class QueueExecutor extends Looper {
    private final Queue<Runnable> queue;
    private boolean shutdown;

    public QueueExecutor() {
        queue = new ShurikenQueue<>();
        shutdown = false;
    }

    public Queue<Runnable> queue() {
        return queue;
    }

    @Override
    protected long loop() {
        while (queue.hasNext()) {
            try {
                queue.next().run();
            } catch (Throwable e) {
                onTaskError(e);
            }
        }

        if (shutdown && !queue.hasNext()) {
            interrupt();
            return -1;
        }

        return Math.max(500, (long) getRunTime() * 10);
    }

    protected void onTaskError(Throwable e) {
        SchedulerBridge.onError(e);
    }

    public double getRunTime() {
        return 0;
    }

    public void shutdown() {
        shutdown = true;
    }
}
