package art.arcane.volmlib.util.scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared startup queue lifecycle support for plugin scheduler wrappers.
 */
public final class StartupQueueSupport {
    private List<Runnable> syncQueue = new ArrayList<>();
    private List<Runnable> asyncQueue = new ArrayList<>();
    private boolean started = false;

    public void execute(Consumer<Runnable> syncScheduler, Consumer<Runnable> asyncScheduler) {
        List<Runnable> syncSnapshot;
        List<Runnable> asyncSnapshot;

        synchronized (this) {
            if (started) {
                return;
            }

            started = true;
            syncSnapshot = syncQueue;
            asyncSnapshot = asyncQueue;
            syncQueue = null;
            asyncQueue = null;
        }

        for (Runnable runnable : syncSnapshot) {
            syncScheduler.accept(runnable);
        }

        for (Runnable runnable : asyncSnapshot) {
            asyncScheduler.accept(runnable);
        }
    }

    public void enqueueSync(Runnable runnable, Consumer<Runnable> syncScheduler) {
        boolean scheduleNow;

        synchronized (this) {
            scheduleNow = started;
            if (!scheduleNow) {
                syncQueue.add(runnable);
            }
        }

        if (scheduleNow) {
            syncScheduler.accept(runnable);
        }
    }

    public void enqueueAsync(Runnable runnable, Consumer<Runnable> asyncScheduler) {
        boolean scheduleNow;

        synchronized (this) {
            scheduleNow = started;
            if (!scheduleNow) {
                asyncQueue.add(runnable);
            }
        }

        if (scheduleNow) {
            asyncScheduler.accept(runnable);
        }
    }
}
