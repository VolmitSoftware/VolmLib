package art.arcane.volmlib.util.mantle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MantleAccessSupportTest {
    @Test
    public void failedBlockingLoadPropagatesWithoutRecursiveRetry() {
        ControlledAccess access = new ControlledAccess();

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> access.accessRegion(2, 7));

        assertSame(access.failure, failure);
        assertEquals(1, access.loads);
        assertEquals(List.of(access.failure), access.reported);
        access.assertPermitsReleased();
    }

    @Test
    public void failedAsyncLoadPropagatesWithoutRecursiveRetry() {
        ControlledAccess access = new ControlledAccess();

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);
        CompletionException failure = assertThrows(CompletionException.class, result::join);

        assertSame(access.failure, failure.getCause());
        assertEquals(1, access.loads);
        assertEquals(List.of(access.failure), access.reported);
        access.assertPermitsReleased();
    }

    @Test
    public void synchronousAsyncLoadFailureReleasesPermits() {
        ControlledAccess access = new ControlledAccess();
        access.throwSynchronously = true;

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);
        CompletionException failure = assertThrows(CompletionException.class, result::join);

        assertSame(access.failure, failure.getCause());
        access.assertPermitsReleased();
    }

    @Test
    public void loadedRegionFailureReleasesAsyncPermits() {
        ControlledAccess access = new ControlledAccess();
        access.failLoadedLookup = true;

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);
        CompletionException failure = assertThrows(CompletionException.class, result::join);

        assertSame(access.failure, failure.getCause());
        assertEquals(0, access.loads);
        access.assertPermitsReleased();
    }

    @Test
    public void pendingAsyncLoadReleasesOnlyItsAcquiredPermitsAfterCompletion() {
        ControlledAccess access = new ControlledAccess();
        access.pending = new CompletableFuture<>();
        assertTrue(access.trimSemaphore().tryAcquire());

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);

        assertFalse(result.isDone());
        assertEquals(0, access.trimSemaphore().availablePermits());
        assertEquals(0, access.unloadSemaphore().availablePermits());

        access.pending.completeExceptionally(access.failure);
        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertSame(access.failure, failure.getCause());
        assertEquals(0, access.trimSemaphore().availablePermits());
        assertEquals(1, access.unloadSemaphore().availablePermits());
        access.trimSemaphore().release();
        access.assertPermitsReleased();
    }

    @Test
    public void laterAccessCanRecoverAfterAnEarlierLoadFailure() {
        ControlledAccess access = new ControlledAccess();
        assertThrows(IllegalStateException.class, () -> access.accessRegion(2, 7));
        access.loaded = "recovered";

        assertEquals("recovered", access.accessRegion(2, 7));
        assertEquals("recovered", access.accessRegionFuture(2, 7).join());
        assertEquals(1, access.loads);
        access.assertPermitsReleased();
    }

    @Test
    public void cancellingAsyncAccessCannotCancelItsPermitCleanup() {
        ControlledAccess access = new ControlledAccess();
        access.pending = new CompletableFuture<>();

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);
        assertTrue(result.cancel(false));

        assertFalse(access.pending.isDone());
        assertEquals(0, access.trimSemaphore().availablePermits());
        assertEquals(0, access.unloadSemaphore().availablePermits());

        assertTrue(access.pending.complete("loaded"));

        assertTrue(result.isCancelled());
        access.assertPermitsReleased();
        assertFalse(access.pending.complete("duplicate"));
        access.assertPermitsReleased();
    }

    @Test
    public void completingAsyncAccessCannotSuppressItsPermitCleanupOrFailureReport() {
        ControlledAccess access = new ControlledAccess();
        access.pending = new CompletableFuture<>();

        CompletableFuture<String> result = access.accessRegionFuture(2, 7);
        assertTrue(result.complete("caller result"));

        assertFalse(access.pending.isDone());
        assertEquals(0, access.trimSemaphore().availablePermits());
        assertEquals(0, access.unloadSemaphore().availablePermits());

        assertTrue(access.pending.completeExceptionally(access.failure));

        assertEquals("caller result", result.join());
        assertEquals(List.of(access.failure), access.reported);
        access.assertPermitsReleased();
        assertFalse(access.pending.completeExceptionally(access.failure));
        access.assertPermitsReleased();
    }

    private static final class ControlledAccess extends MantleAccessSupport<String> {
        private final IllegalStateException failure = new IllegalStateException("region load failed");
        private final ArrayList<Throwable> reported = new ArrayList<>();
        private int loads;
        private boolean throwSynchronously;
        private boolean failLoadedLookup;
        private String loaded;
        private CompletableFuture<String> pending;

        private ControlledAccess() {
            super(1);
        }

        @Override
        protected String regionName() {
            return "test region";
        }

        @Override
        protected CompletableFuture<String> loadRegionSafe(int x, int z) {
            loads++;
            if (loads > 3) {
                return CompletableFuture.completedFuture("unexpected retry");
            }
            if (throwSynchronously) {
                throw failure;
            }
            return pending == null ? CompletableFuture.failedFuture(failure) : pending;
        }

        @Override
        protected String loadRegionBlocking(int x, int z) {
            if (++loads > 3) {
                return "unexpected retry";
            }
            throw failure;
        }

        @Override
        protected String getLoadedRegion(int x, int z) {
            return loaded;
        }

        @Override
        protected String acquireLoadedRegion(int x, int z) {
            if (failLoadedLookup) {
                throw failure;
            }
            return loaded;
        }

        @Override
        protected boolean isRegionClosed(String region) {
            return false;
        }

        @Override
        protected void markRegionUsed(int x, int z, String region) {
        }

        @Override
        protected void warn(String message) {
        }

        @Override
        protected void report(Throwable throwable) {
            reported.add(throwable);
        }

        private void assertPermitsReleased() {
            assertEquals(1, trimSemaphore().availablePermits());
            assertEquals(1, unloadSemaphore().availablePermits());
        }
    }
}
