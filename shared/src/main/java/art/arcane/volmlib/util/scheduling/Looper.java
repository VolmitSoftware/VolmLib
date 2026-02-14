package art.arcane.volmlib.util.scheduling;

public abstract class Looper extends Thread {
    @Override
    @SuppressWarnings("BusyWait")
    public void run() {
        onStart();

        while (!interrupted()) {
            try {
                long m = loop();

                if (m < 0) {
                    break;
                }

                Thread.sleep(m);
            } catch (InterruptedException e) {
                onInterrupted(e);
                break;
            } catch (Throwable e) {
                onError(e);
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
