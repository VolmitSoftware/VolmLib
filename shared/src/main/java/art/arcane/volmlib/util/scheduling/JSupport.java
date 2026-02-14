package art.arcane.volmlib.util.scheduling;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class JSupport {
    private JSupport() {
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R run(T t) throws Throwable;
    }

    public static void dofor(int a, Function<Integer, Boolean> c, int ch, Consumer<Integer> d) {
        for (int i = a; c.apply(i); i += ch) {
            d.accept(i);
        }
    }

    public static boolean doif(Supplier<Boolean> c, Runnable g, Consumer<Throwable> errorHandler) {
        try {
            if (c.get()) {
                g.run();
                return true;
            }
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }

        return false;
    }

    public static void attemptAsync(ThrowingRunnable r, Consumer<Runnable> asyncScheduler) {
        asyncScheduler.accept(() -> attempt(r));
    }

    public static void executeAfterStartupQueue(
            StartupQueueSupport startupQueue,
            Consumer<Runnable> syncScheduler,
            Consumer<Runnable> asyncScheduler
    ) {
        startupQueue.execute(syncScheduler, asyncScheduler);
    }

    public static void enqueueAfterStartupSync(
            StartupQueueSupport startupQueue,
            Runnable runnable,
            Consumer<Runnable> syncScheduler
    ) {
        startupQueue.enqueueSync(runnable, syncScheduler);
    }

    public static void enqueueAfterStartupAsync(
            StartupQueueSupport startupQueue,
            Runnable runnable,
            Consumer<Runnable> asyncScheduler
    ) {
        startupQueue.enqueueAsync(runnable, asyncScheduler);
    }

    public static boolean sleep(long ms) {
        return attempt(() -> Thread.sleep(ms));
    }

    public static boolean attempt(ThrowingRunnable r) {
        return attemptCatch(r) == null;
    }

    public static Throwable attemptCatch(ThrowingRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            return e;
        }

        return null;
    }

    public static <T> T attempt(ThrowingSupplier<T> supplier, T fallback, Consumer<Throwable> errorHandler) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }

            return fallback;
        }
    }

    public static <R> R attemptResult(ThrowingSupplier<R> supplier, R onError, Consumer<Throwable> errorHandler) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }

        return onError;
    }

    public static <T, R> R attemptFunction(ThrowingFunction<T, R> function, T param, R onError, Consumer<Throwable> errorHandler) {
        try {
            return function.run(param);
        } catch (Throwable e) {
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }

        return onError;
    }

    public static <T> T attemptNullable(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable e) {
            return null;
        }
    }
}
