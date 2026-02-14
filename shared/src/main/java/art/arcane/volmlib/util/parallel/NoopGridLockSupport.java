package art.arcane.volmlib.util.parallel;

import art.arcane.volmlib.util.function.NastyRunnable;
import art.arcane.volmlib.util.io.IORunnable;

import java.io.IOException;
import java.util.function.Supplier;

public class NoopGridLockSupport extends GridLockSupport {
    public NoopGridLockSupport(int x, int z) {
        super(x, z);
    }

    @Override
    public void with(int x, int z, Runnable r) {
        r.run();
    }

    @Override
    public void withNasty(int x, int z, NastyRunnable r) throws Throwable {
        r.run();
    }

    @Override
    public void withIO(int x, int z, IORunnable r) throws IOException {
        r.run();
    }

    @Override
    public <T> T withResult(int x, int z, Supplier<T> r) {
        return r.get();
    }

    @Override
    public void withAll(Runnable r) {
        r.run();
    }

    @Override
    public <T> T withAllResult(Supplier<T> r) {
        return r.get();
    }

    @Override
    public boolean tryLock(int x, int z) {
        return true;
    }

    @Override
    public boolean tryLock(int x, int z, long timeout) {
        return true;
    }

    @Override
    public void lock(int x, int z) {
    }

    @Override
    public void unlock(int x, int z) {
    }
}
