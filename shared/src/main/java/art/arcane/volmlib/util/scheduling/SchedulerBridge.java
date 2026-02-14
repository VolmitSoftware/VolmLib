package art.arcane.volmlib.util.scheduling;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class SchedulerBridge {
    private static volatile Consumer<Runnable> syncScheduler = Runnable::run;
    private static volatile BiConsumer<Runnable, Integer> delayedSyncScheduler = (r, d) -> syncScheduler.accept(r);
    private static volatile Consumer<Runnable> asyncScheduler = Runnable::run;
    private static volatile BiConsumer<Runnable, Integer> delayedAsyncScheduler = (r, d) -> asyncScheduler.accept(r);
    private static volatile BiFunction<Runnable, Integer, Integer> syncRepeatingScheduler = (r, i) -> -1;
    private static volatile BiFunction<Runnable, Integer, Integer> asyncRepeatingScheduler = (r, i) -> -1;
    private static volatile IntConsumer cancelScheduler = (taskId) -> {
    };
    private static volatile Consumer<Throwable> errorHandler = Throwable::printStackTrace;
    private static volatile Consumer<String> infoLogger = (message) -> {
    };
    private static volatile Consumer<Thread> threadRegistrar = (thread) -> {
    };

    private SchedulerBridge() {
    }

    public static void setSyncScheduler(Consumer<Runnable> scheduler) {
        if (scheduler != null) {
            syncScheduler = scheduler;
        }
    }

    public static void setDelayedSyncScheduler(BiConsumer<Runnable, Integer> scheduler) {
        if (scheduler != null) {
            delayedSyncScheduler = scheduler;
        }
    }

    public static void setAsyncScheduler(Consumer<Runnable> scheduler) {
        if (scheduler != null) {
            asyncScheduler = scheduler;
        }
    }

    public static void setDelayedAsyncScheduler(BiConsumer<Runnable, Integer> scheduler) {
        if (scheduler != null) {
            delayedAsyncScheduler = scheduler;
        }
    }

    public static void setSyncRepeatingScheduler(BiFunction<Runnable, Integer, Integer> scheduler) {
        if (scheduler != null) {
            syncRepeatingScheduler = scheduler;
        }
    }

    public static void setAsyncRepeatingScheduler(BiFunction<Runnable, Integer, Integer> scheduler) {
        if (scheduler != null) {
            asyncRepeatingScheduler = scheduler;
        }
    }

    public static void setCancelScheduler(IntConsumer scheduler) {
        if (scheduler != null) {
            cancelScheduler = scheduler;
        }
    }

    public static void setErrorHandler(Consumer<Throwable> handler) {
        if (handler != null) {
            errorHandler = handler;
        }
    }

    public static void setInfoLogger(Consumer<String> logger) {
        if (logger != null) {
            infoLogger = logger;
        }
    }

    public static void setThreadRegistrar(Consumer<Thread> registrar) {
        if (registrar != null) {
            threadRegistrar = registrar;
        }
    }

    public static void scheduleSync(Runnable runnable) {
        syncScheduler.accept(runnable);
    }

    public static void scheduleSync(Runnable runnable, int delay) {
        if (delay <= 0) {
            syncScheduler.accept(runnable);
            return;
        }

        delayedSyncScheduler.accept(runnable, delay);
    }

    public static void scheduleAsync(Runnable runnable) {
        asyncScheduler.accept(runnable);
    }

    public static void scheduleAsync(Runnable runnable, int delay) {
        if (delay <= 0) {
            asyncScheduler.accept(runnable);
            return;
        }

        delayedAsyncScheduler.accept(runnable, delay);
    }

    public static int scheduleSyncRepeating(Runnable runnable, int interval) {
        return syncRepeatingScheduler.apply(runnable, interval);
    }

    public static int scheduleAsyncRepeating(Runnable runnable, int interval) {
        return asyncRepeatingScheduler.apply(runnable, interval);
    }

    public static void cancel(int taskId) {
        cancelScheduler.accept(taskId);
    }

    public static void onError(Throwable throwable) {
        errorHandler.accept(throwable);
    }

    public static void logInfo(String message) {
        infoLogger.accept(message);
    }

    public static void registerThread(Thread thread) {
        threadRegistrar.accept(thread);
    }
}
