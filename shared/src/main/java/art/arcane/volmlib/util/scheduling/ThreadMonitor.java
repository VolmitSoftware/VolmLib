package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.RollingSequence;

/**
 * Not particularly efficient or perfectly accurate but is great at fast thread
 * switching detection.
 */
public class ThreadMonitor extends Thread {
    private final Thread monitor;
    private final ChronoLatch cl;
    private final RollingSequence sq = new RollingSequence(3);
    int cycles = 0;
    private boolean running;
    private State lastState;
    private PrecisionStopwatch st;

    protected ThreadMonitor(Thread monitor) {
        running = true;
        st = PrecisionStopwatch.start();
        this.monitor = monitor;
        lastState = State.NEW;
        cl = new ChronoLatch(1000);
        start();
    }

    public static ThreadMonitor bind(Thread monitor) {
        return new ThreadMonitor(monitor);
    }

    @Override
    @SuppressWarnings("BusyWait")
    public void run() {
        while (running) {
            try {
                Thread.sleep(0);
                State s = monitor.getState();
                if (lastState != s) {
                    cycles++;
                    pushState(s);
                }

                lastState = s;

                if (cl.flip()) {
                    onCycleReport(cycles, sq.getAverage());
                }
            } catch (Throwable e) {
                onMonitorError(e);
                running = false;
                break;
            }
        }
    }

    protected void onCycleReport(int cycles, double avgStateMs) {
        SchedulerBridge.logInfo("Cycles: " + Form.f(cycles) + " (" + Form.duration(avgStateMs, 2) + ")");
    }

    protected void onMonitorError(Throwable e) {
        SchedulerBridge.onError(e);
    }

    public void pushState(State s) {
        if (s != State.RUNNABLE) {
            if (st != null) {
                sq.put(st.getMilliseconds());
            }
        } else {
            st = PrecisionStopwatch.start();
        }
    }

    public void unbind() {
        running = false;
    }
}
