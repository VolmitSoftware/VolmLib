package art.arcane.volmlib.util.scheduling;

import art.arcane.volmlib.util.VolmLog;

import java.util.concurrent.locks.ReentrantLock;

public class IrisLock {
    private final ReentrantLock lock;
    private final String name;
    private boolean disabled = false;

    public IrisLock(String name) {
        this.name = name;
        lock = new ReentrantLock(false);
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public String getName() {
        return name;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public IrisLock setDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public void lock() {
        if (disabled) {
            return;
        }

        lock.lock();
    }

    public void unlock() {
        if (disabled) {
            return;
        }

        try {
            lock.unlock();
        } catch (Throwable e) {
            VolmLog.warning("Lock", "Could not release lock " + name, e);
        }
    }
}
