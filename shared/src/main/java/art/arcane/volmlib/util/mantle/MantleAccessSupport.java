package art.arcane.volmlib.util.mantle;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;

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
        boolean trim = trimSemaphore.tryAcquire();
        boolean unload = unloadSemaphore.tryAcquire();
        try {
            if (!trim || !unload) {
                try {
                    return loadRegionSafe(x, z).get();
                } catch (Throwable e) {
                    report(e);
                }
            } else {
                P p = getLoadedRegion(x, z);
                if (p != null && !isRegionClosed(p)) {
                    markRegionUsed(x, z, p);
                    return p;
                }
            }

            try {
                return loadRegionSafe(x, z).get();
            } catch (InterruptedException e) {
                warn("Failed to get " + regionName() + " " + x + " " + z + " due to thread interruption");
                report(e);
            } catch (ExecutionException e) {
                warn("Failed to get " + regionName() + " " + x + " " + z + " due to execution exception");
                report(e);
            } catch (Throwable e) {
                warn("Failed to get " + regionName() + " " + x + " " + z + " due to unknown exception");
                report(e);
            }
        } finally {
            if (trim) {
                trimSemaphore.release();
            }
            if (unload) {
                unloadSemaphore.release();
            }
        }

        warn("Retrying to get " + x + " " + z + " " + regionRetryName());
        return accessRegion(x, z);
    }

    protected CompletableFuture<P> accessRegionFuture(int x, int z) {
        final boolean trim = trimSemaphore.tryAcquire();
        final boolean unload = unloadSemaphore.tryAcquire();
        final Function<P, P> release = p -> {
            if (trim) {
                trimSemaphore.release();
            }
            if (unload) {
                unloadSemaphore.release();
            }
            return p;
        };

        final Supplier<CompletableFuture<P>> fallback = () -> loadRegionSafe(x, z)
                .exceptionally(e -> {
                    Throwable root = rootCause(e);
                    if (root instanceof InterruptedException) {
                        warn("Failed to get " + regionName() + " " + x + " " + z + " due to thread interruption");
                    } else {
                        warn("Failed to get " + regionName() + " " + x + " " + z + " due to unknown exception");
                    }

                    report(root);
                    return null;
                })
                .thenCompose(p -> {
                    release.apply(p);
                    if (p != null) {
                        return CompletableFuture.completedFuture(p);
                    }

                    warn("Retrying to get " + x + " " + z + " " + regionRetryName());
                    return accessRegionFuture(x, z);
                });

        if (!trim || !unload) {
            return loadRegionSafe(x, z)
                    .thenApply(release)
                    .exceptionallyCompose(e -> {
                        report(rootCause(e));
                        return fallback.get();
                    });
        }

        P p = getLoadedRegion(x, z);
        if (p != null && !isRegionClosed(p)) {
            markRegionUsed(x, z, p);
            return CompletableFuture.completedFuture(release.apply(p));
        }

        return fallback.get();
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

    protected abstract P getLoadedRegion(int x, int z);

    protected abstract boolean isRegionClosed(P region);

    protected abstract void markRegionUsed(int x, int z, P region);

    protected abstract void warn(String message);

    protected abstract void report(Throwable throwable);
}
