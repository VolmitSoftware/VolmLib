package art.arcane.volmlib.util.mantle;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Shared concurrent access support for mantle-like region tables.
 */
public abstract class MantleAccessSupport<P> {
    private final Semaphore trimSemaphore;
    private final Semaphore unloadSemaphore;

    protected MantleAccessSupport(int lockSize) {
        int size = Math.max(lockSize, 1);
        this.trimSemaphore = new Semaphore(size, true);
        this.unloadSemaphore = new Semaphore(size, true);
    }

    protected Semaphore trimSemaphore() {
        return trimSemaphore;
    }

    protected Semaphore unloadSemaphore() {
        return unloadSemaphore;
    }

    protected P accessRegion(int x, int z) {
        boolean unload = unloadSemaphore.tryAcquire();
        try {
            // While this thread holds an unload permit no eviction or flush can run (they need
            // every permit), so the loaded plate can be read without the region lock.
            P loaded = unload ? acquireLoadedRegionGuarded(x, z) : acquireLoadedRegion(x, z);
            if (loaded != null) {
                return loaded;
            }

            return loadRegionBlocking(x, z);
        } catch (RuntimeException | Error failure) {
            reportAccessFailure(x, z, failure);
            throw failure;
        } finally {
            if (unload) {
                unloadSemaphore.release();
            }
        }
    }

    protected CompletableFuture<P> accessRegionFuture(int x, int z) {
        boolean trim = trimSemaphore.tryAcquire();
        boolean unload = unloadSemaphore.tryAcquire();
        CompletableFuture<P> access;
        try {
            P loaded = acquireLoadedRegion(x, z);
            access = loaded != null
                    ? CompletableFuture.completedFuture(loaded)
                    : Objects.requireNonNull(loadRegionSafe(x, z), "Region load returned no completion future");
        } catch (Throwable failure) {
            access = CompletableFuture.failedFuture(failure);
        }
        return access.whenComplete((region, failure) -> {
            if (trim) {
                trimSemaphore.release();
            }
            if (unload) {
                unloadSemaphore.release();
            }
            if (failure != null) {
                reportAccessFailure(x, z, rootCause(failure));
            }
        }).copy();
    }

    private void reportAccessFailure(int x, int z, Throwable failure) {
        warn("Failed to access " + regionRetryName() + " " + x + " " + z);
        report(failure);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }

            current = current.getCause();
        }
        return current;
    }

    protected String regionRetryName() {
        return regionName();
    }

    protected abstract String regionName();

    protected abstract CompletableFuture<P> loadRegionSafe(int x, int z);
    protected abstract P loadRegionBlocking(int x, int z);

    protected abstract P getLoadedRegion(int x, int z);

    protected abstract P acquireLoadedRegion(int x, int z);

    /**
     * The loaded, open region at the coordinates, or null. Called only while the caller holds an
     * unload permit; implementations may skip the region lock the unguarded variant takes.
     */
    protected P acquireLoadedRegionGuarded(int x, int z) {
        return acquireLoadedRegion(x, z);
    }

    protected abstract boolean isRegionClosed(P region);

    protected abstract void markRegionUsed(int x, int z, P region);

    protected abstract void warn(String message);

    protected abstract void report(Throwable throwable);
}
