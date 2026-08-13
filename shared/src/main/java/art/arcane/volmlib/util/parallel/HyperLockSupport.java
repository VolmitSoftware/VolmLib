package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.function.NastySupplier;
import art.arcane.volmlib.util.io.IORunnable;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class HyperLockSupport {
    private final ConcurrentMap<Long, OwnedLock> locks;
    private final Function<Long, OwnedLock> lockFactory;
    private final Consumer<String> warningHandler;
    private final Consumer<Throwable> errorHandler;
    private volatile boolean enabled = true;

    public HyperLockSupport() {
        this(1024, false, null, null);
    }

    public HyperLockSupport(int capacity) {
        this(capacity, false, null, null);
    }

    public HyperLockSupport(int capacity, boolean fair) {
        this(capacity, fair, null, null);
    }

    public HyperLockSupport(int capacity, boolean fair, Consumer<String> warningHandler, Consumer<Throwable> errorHandler) {
        this.warningHandler = warningHandler;
        this.errorHandler = errorHandler;
        this.lockFactory = key -> new OwnedLock(fair);
        locks = new ConcurrentHashMap<>(Math.max(capacity, 64));
    }

    public void with(int x, int z, Runnable r) {
        lock(x, z);
        try {
            r.run();
        } finally {
            unlock(x, z);
        }
    }

    public void withLong(long k, Runnable r) {
        int x = CacheKey.keyX(k), z = CacheKey.keyZ(k);
        lock(x, z);
        try {
            r.run();
        } finally {
            unlock(x, z);
        }
    }

    public void withNasty(int x, int z, NastyRunnable r) throws Throwable {
        lock(x, z);
        Throwable ee = null;
        try {
            r.run();
        } catch (Throwable e) {
            ee = e;
        } finally {
            unlock(x, z);

            if (ee != null) {
                throw ee;
            }
        }
    }

    public void withIO(int x, int z, IORunnable r) throws IOException {
        lock(x, z);
        IOException ee = null;
        try {
            r.run();
        } catch (IOException e) {
            ee = e;
        } finally {
            unlock(x, z);

            if (ee != null) {
                throw ee;
            }
        }
    }

    public <T> T withResult(int x, int z, Supplier<T> r) {
        lock(x, z);
        try {
            return r.get();
        } finally {
            unlock(x, z);
        }
    }

    public <T> T withNastyResult(int x, int z, NastySupplier<T> r) throws Throwable {
        lock(x, z);
        try {
            return r.get();
        } finally {
            unlock(x, z);
        }
    }

    public boolean tryLock(int x, int z) {
        return getLock(x, z).tryLock();
    }

    public boolean tryLock(int x, int z, long timeout) {
        try {
            return getLock(x, z).tryLock(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
        }

        return false;
    }

    private OwnedLock getLock(int x, int z) {
        Long key = CacheKey.key(x, z);
        OwnedLock lock = locks.get(key);
        return lock != null ? lock : locks.computeIfAbsent(key, lockFactory);
    }

    /**
     * Records the acquisition time for the wait diagnostics. Skipped entirely when no warning
     * handler is installed, so the uncontended path stays free of clock reads.
     */
    private void stamp(OwnedLock lock) {
        if (warningHandler != null) {
            lock.acquiredAtMs = System.currentTimeMillis();
        }
    }

    public void lock(int x, int z) {
        if (!enabled) {
            return;
        }

        OwnedLock lock = getLock(x, z);
        if (lock.tryLock()) {
            stamp(lock);
            return;
        }

        long started = System.currentTimeMillis();
        while (enabled) {
            try {
                if (lock.tryLock(5, TimeUnit.SECONDS)) {
                    stamp(lock);
                    long waited = System.currentTimeMillis() - started;
                    if (warningHandler != null && waited >= 1000L) {
                        warningHandler.accept("HyperLock acquired after wait: key=" + x + "," + z
                                + " waitedMs=" + waited
                                + " thread=" + Thread.currentThread().getName());
                    }
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
                return;
            }

            if (warningHandler != null) {
                long waited = System.currentTimeMillis() - started;
                Thread owner = lock.owner();
                String ownerSummary = owner == null
                        ? "none"
                        : owner.getName() + " alive=" + owner.isAlive() + " heldMs=" + (System.currentTimeMillis() - lock.acquiredAtMs);
                warningHandler.accept("HyperLock waiting: key=" + x + "," + z
                        + " waitedMs=" + waited
                        + " thread=" + Thread.currentThread().getName()
                        + " holdCount=" + lock.getHoldCount()
                        + " isLocked=" + lock.isLocked()
                        + " owner=" + ownerSummary);
                if (owner != null && owner.isAlive()) {
                    StackTraceElement[] trace = owner.getStackTrace();
                    int limit = Math.min(trace.length, 12);
                    for (int i = 0; i < limit; i++) {
                        warningHandler.accept("  owner-at " + trace[i]);
                    }
                }
            }
        }
    }

    public void unlock(int x, int z) {
        // Ownership-based, never flag-based: a thread that acquired before disable() flipped
        // `enabled` must still release, or disable() parks forever on a lock nobody will
        // unlock. A thread that skipped acquisition (disabled) never holds it, so this can
        // never throw IllegalMonitorStateException.
        OwnedLock lock = getLock(x, z);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    public void disable() {
        enabled = false;
        // Bounded: a wedged holder degrades to a loud warning instead of hanging shutdown.
        for (OwnedLock lock : locks.values()) {
            try {
                if (!lock.tryLock(10_000L, java.util.concurrent.TimeUnit.MILLISECONDS) && warningHandler != null) {
                    warningHandler.accept("HyperLock disable timed out waiting for a held lock; continuing shutdown");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Per-key lock that carries its own owner diagnostics. Keeping the acquisition stamp on the
     * lock removes the second concurrent map plus an owner allocation from every acquisition;
     * the holder thread comes straight from the AQS state.
     */
    private static final class OwnedLock extends ReentrantLock {
        private static final long serialVersionUID = 1L;

        private volatile long acquiredAtMs;

        private OwnedLock(boolean fair) {
            super(fair);
        }

        private Thread owner() {
            return getOwner();
        }
    }
}
