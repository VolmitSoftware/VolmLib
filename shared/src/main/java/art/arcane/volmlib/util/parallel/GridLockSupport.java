package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.io.IORunnable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GridLockSupport {
    private final ReentrantLock[][] locks;
    private final Consumer<Throwable> errorHandler;

    public GridLockSupport(int x, int z) {
        this(x, z, null);
    }

    public GridLockSupport(int x, int z, Consumer<Throwable> errorHandler) {
        this.errorHandler = errorHandler;
        this.locks = new ReentrantLock[x][z];
        for (int xx = 0; xx < x; xx++) {
            for (int zz = 0; zz < z; zz++) {
                locks[xx][zz] = new ReentrantLock();
            }
        }
    }

    public void with(int x, int z, Runnable r) {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public void withNasty(int x, int z, NastyRunnable r) throws Throwable {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public void withIO(int x, int z, IORunnable r) throws IOException {
        lock(x, z);
        r.run();
        unlock(x, z);
    }

    public <T> T withResult(int x, int z, Supplier<T> r) {
        lock(x, z);
        T t = r.get();
        unlock(x, z);
        return t;
    }

    public void withAll(Runnable r) {
        forEachLock(ReentrantLock::lock);
        r.run();
        forEachLock(ReentrantLock::unlock);
    }

    public <T> T withAllResult(Supplier<T> r) {
        forEachLock(ReentrantLock::lock);
        T t = r.get();
        forEachLock(ReentrantLock::unlock);

        return t;
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

    public void lock(int x, int z) {
        getLock(x, z).lock();
    }

    public void unlock(int x, int z) {
        getLock(x, z).unlock();
    }

    protected ReentrantLock getLock(int x, int z) {
        return locks[x][z];
    }

    private void forEachLock(Consumer<ReentrantLock> action) {
        for (ReentrantLock[] row : locks) {
            for (ReentrantLock lock : row) {
                action.accept(lock);
            }
        }
    }
}
