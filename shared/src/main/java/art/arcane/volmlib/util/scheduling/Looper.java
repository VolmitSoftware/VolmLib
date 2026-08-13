package art.arcane.volmlib.util.scheduling;

public abstract class Looper extends Thread {
    @Override
    @SuppressWarnings("BusyWait")
    public void run() {
        onStart();
        long backoff = 0L;

        while (!interrupted()) {
            long m;
            try {
                m = loop();
                backoff = 0L;
            } catch (Throwable e) {
                if (e instanceof InterruptedException interrupt) {
                    onInterrupted(interrupt);
                    break;
                }
                onError(e);
                // Pace a throwing loop() instead of re-entering it immediately; an
                // unpaced deterministic failure is a 100% CPU spin that floods the log.
                backoff = Math.min(Math.max(backoff * 2L, 1_000L), 60_000L);
                m = backoff;
            }

            if (m < 0) {
                break;
            }

            try {
                Thread.sleep(m);
            } catch (InterruptedException e) {
                onInterrupted(e);
                break;
            }
        }

        onStop();
    }

    protected void onStart() {
        SchedulerBridge.registerThread(this);
    }

    protected void onInterrupted(InterruptedException e) {
    }

    protected void onError(Throwable e) {
        SchedulerBridge.onError(e);
    }

    protected void onStop() {
        SchedulerBridge.logInfo("Thread " + getName() + " Shutdown.");
    }

    protected abstract long loop();
}
