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
import java.util.function.Supplier;

public class HyperLockSupport {
    private final ConcurrentMap<Long, ReentrantLock> locks;
    private final ConcurrentMap<Long, LockOwner> owners;
    private final Consumer<String> warningHandler;
    private final Consumer<Throwable> errorHandler;
    private volatile boolean enabled = true;
    private final boolean fair;

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
        this.fair = fair;
        this.warningHandler = warningHandler;
        this.errorHandler = errorHandler;
        locks = new ConcurrentHashMap<>(Math.max(capacity, 64));
        owners = new ConcurrentHashMap<>(Math.max(capacity, 64));
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

    private ReentrantLock getLock(int x, int z) {
        return locks.computeIfAbsent(CacheKey.key(x, z), k -> new ReentrantLock(fair));
    }

    public void lock(int x, int z) {
        if (!enabled) {
            return;
        }

        long key = CacheKey.key(x, z);
        ReentrantLock lock = getLock(x, z);
        if (lock.tryLock()) {
            owners.put(key, new LockOwner(Thread.currentThread(), System.currentTimeMillis()));
            return;
        }

        long started = System.currentTimeMillis();
        while (enabled) {
            try {
                if (lock.tryLock(5, TimeUnit.SECONDS)) {
                    owners.put(key, new LockOwner(Thread.currentThread(), System.currentTimeMillis()));
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
                LockOwner owner = owners.get(key);
                String ownerSummary = owner == null
                        ? "none"
                        : owner.thread().getName() + " alive=" + owner.thread().isAlive() + " heldMs=" + (System.currentTimeMillis() - owner.acquiredAtMs());
                warningHandler.accept("HyperLock waiting: key=" + x + "," + z
                        + " waitedMs=" + waited
                        + " thread=" + Thread.currentThread().getName()
                        + " holdCount=" + lock.getHoldCount()
                        + " isLocked=" + lock.isLocked()
                        + " owner=" + ownerSummary);
                if (owner != null && owner.thread().isAlive()) {
                    StackTraceElement[] trace = owner.thread().getStackTrace();
                    int limit = Math.min(trace.length, 12);
                    for (int i = 0; i < limit; i++) {
                        warningHandler.accept("  owner-at " + trace[i]);
                    }
                }
            }
        }
    }

    public void unlock(int x, int z) {
        if (!enabled) {
            return;
        }

        long key = CacheKey.key(x, z);
        ReentrantLock lock = getLock(x, z);
        int holdCount = lock.getHoldCount();
        lock.unlock();
        if (holdCount <= 1) {
            owners.remove(key);
        }
    }

    public void disable() {
        enabled = false;
        locks.values().forEach(ReentrantLock::lock);
    }

    private record LockOwner(Thread thread, long acquiredAtMs) {
    }
}
