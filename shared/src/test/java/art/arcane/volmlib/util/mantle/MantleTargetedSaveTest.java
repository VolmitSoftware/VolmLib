package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MantleTargetedSaveTest {
    private static final MantleDataAdapter<TestSection> ADAPTER = new TestAdapter();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadedFlagQueriesNeverCreateChunksOrReloadSavedPlates() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("loaded-flags"))) {
            assertFalse(runtime.mantle.hasLoadedFlag(-1, 2, MantleFlag.REAL));
            assertFalse(runtime.mantle.withLoadedChunk(-1, 2, chunk -> {
                throw new AssertionError("Missing chunks must not be created");
            }));
            assertEquals(0, runtime.mantle.getLoadedRegionCount());
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(-1, 2);
            TectonicPlate<TestSection> plate = runtime.mantle.getLoadedRegions().get(Mantle.key(-1, 0));
            chunk.flag(MantleFlag.REAL, true);
            assertTrue(runtime.mantle.hasLoadedFlag(-1, 2, MantleFlag.REAL));
            assertFalse(runtime.mantle.hasLoadedFlag(-1, 2, MantleFlag.CLEANED));
            assertFalse(runtime.mantle.hasLoadedFlag(-2, 2, MantleFlag.REAL));
            assertFalse(runtime.mantle.withLoadedChunk(-2, 2, missing -> {
                throw new AssertionError("Missing resident chunk must not be created");
            }));
            assertNull(plate.get(30, 2));

            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(Mantle.key(-1, 0))));
            assertTrue(Mantle.fileForRegion(runtime.mantle.getDataFolder(), -1, 0).createNewFile());
            assertFalse(runtime.mantle.hasLoadedFlag(-1, 2, MantleFlag.REAL));
            assertFalse(runtime.mantle.withLoadedChunk(-1, 2, missing -> {
                throw new AssertionError("Saved plates must not be reloaded");
            }));
            assertEquals(0, runtime.mantle.getLoadedRegionCount());
        }
    }

    @Test
    public void loadedAccessSkipsSealedPlateWithoutWaitingForItsWrite() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("sealed-loaded-access"))) {
            runtime.mantle.getChunk(0, 0).flag(MantleFlag.REAL, true);
            long id = Mantle.key(0, 0);
            runtime.regionIo.blockWritesFor(id);
            Future<Set<Long>> save = executor.submit(() -> runtime.mantle.saveIdleTectonicPlates(List.of(id)));
            try {
                assertTrue(runtime.regionIo.writeEntered.await(1L, TimeUnit.SECONDS));
                assertTrue(runtime.mantle.isChunkLoaded(0, 0));
                assertFalse(runtime.mantle.hasLoadedFlag(0, 0, MantleFlag.REAL));
                assertFalse(runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                    throw new AssertionError("Sealed plate must not be mutated");
                }));
            } finally {
                runtime.regionIo.releaseBlockedWrite();
            }
            assertEquals(Set.of(), save.get(1L, TimeUnit.SECONDS));
            assertEquals(0, runtime.mantle.getLoadedRegionCount());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void loadedChunkMutationDefersConcurrentEvictionUntilItFinishes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("guarded-loaded-mutation"))) {
            runtime.mantle.getChunk(0, 0);
            long id = Mantle.key(0, 0);
            assertTrue(runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                Future<Set<Long>> save = executor.submit(() -> runtime.mantle.saveIdleTectonicPlates(List.of(id)));
                try {
                    assertEquals(Set.of(id), save.get(1L, TimeUnit.SECONDS));
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                assertFalse(chunk.isClosed());
                chunk.flag(MantleFlag.REAL, true);
                return true;
            }));
            assertTrue(runtime.mantle.hasLoadedFlag(0, 0, MantleFlag.REAL));
            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(id)));
            assertEquals(0, runtime.mantle.getLoadedRegionCount());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void loadedChunkMutationReleasesRegionLockAfterFailure() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("failed-loaded-mutation"))) {
            runtime.mantle.getChunk(0, 0);
            IllegalArgumentException expected = new IllegalArgumentException("mutation failed");
            assertSame(expected, assertThrows(IllegalArgumentException.class,
                    () -> runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                        throw expected;
                    })));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                assertEquals(Set.of(), executor.submit(() -> runtime.mantle.saveIdleTectonicPlates(
                        List.of(Mantle.key(0, 0)))).get(1L, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void loadedAccessDoesNotWaitForUnloadPermitsHeldByItsCaller() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("loaded-unload-order"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.getChunk(0, 0).flag(MantleFlag.REAL, true);
            mantle.unloadSemaphore().acquireUninterruptibly();
            Future<?> flush = executor.submit(mantle::saveAll);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
                while (!mantle.unloadSemaphore().hasQueuedThreads() && System.nanoTime() < deadline) {
                    Thread.sleep(1L);
                }
                assertTrue(mantle.unloadSemaphore().hasQueuedThreads());
                Future<Boolean> access = executor.submit(() -> {
                    assertTrue(mantle.hasLoadedFlag(0, 0, MantleFlag.REAL));
                    return mantle.withLoadedChunk(0, 0, chunk -> {
                        chunk.flag(MantleFlag.CLEANED, true);
                        return true;
                    });
                });
                assertTrue(access.get(1L, TimeUnit.SECONDS));
            } finally {
                mantle.unloadSemaphore().release();
            }
            flush.get(1L, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void waitingForChunkMonitorDoesNotHoldTheRegionLock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("loaded-monitor-order"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            MantleChunk<TestSection> chunk = mantle.getChunk(0, 0);
            int permits = mantle.unloadSemaphore().availablePermits();
            mantle.unloadSemaphore().acquireUninterruptibly(permits);
            AtomicReference<Thread> mutationThread = new AtomicReference<>();
            Future<Boolean> mutation;
            try {
                synchronized (chunk) {
                    mutation = executor.submit(() -> {
                        mutationThread.set(Thread.currentThread());
                        return mantle.withLoadedChunk(0, 0, existing -> {
                            synchronized (existing) {
                                existing.flag(MantleFlag.CLEANED, true);
                                return true;
                            }
                        });
                    });
                    awaitBlocked(mutationThread);
                    Future<MantleChunk<TestSection>> lookup = executor.submit(() -> mantle.getChunk(0, 0));
                    assertSame(chunk, lookup.get(1L, TimeUnit.SECONDS));
                }
                assertTrue(mutation.get(1L, TimeUnit.SECONDS));
            } finally {
                mantle.unloadSemaphore().release(permits);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void loadedMutationRejectsPlateReplacedWhileWaitingForChunkMonitor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("loaded-stale-plate"))) {
            MantleChunk<TestSection> original = runtime.mantle.getChunk(0, 0);
            AtomicReference<Thread> mutationThread = new AtomicReference<>();
            Future<Boolean> mutation;
            MantleChunk<TestSection> replacement;
            synchronized (original) {
                mutation = executor.submit(() -> {
                    mutationThread.set(Thread.currentThread());
                    return runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                        throw new AssertionError("Stale chunk must not be mutated");
                    });
                });
                awaitBlocked(mutationThread);
                assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(Mantle.key(0, 0))));
                replacement = runtime.mantle.getChunk(0, 0);
                assertNotSame(original, replacement);
            }
            assertFalse(mutation.get(1L, TimeUnit.SECONDS));
            assertTrue(runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                assertSame(replacement, chunk);
                return true;
            }));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void loadedMutationRejectsChunkReplacedWithinTheSamePlate() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("loaded-stale-chunk"))) {
            MantleChunk<TestSection> original = runtime.mantle.getChunk(0, 0);
            TectonicPlate<TestSection> plate = runtime.mantle.getLoadedRegions().get(Mantle.key(0, 0));
            AtomicReference<Thread> mutationThread = new AtomicReference<>();
            Future<Boolean> mutation;
            synchronized (original) {
                mutation = executor.submit(() -> {
                    mutationThread.set(Thread.currentThread());
                    return runtime.mantle.withLoadedChunk(0, 0, chunk -> {
                        throw new AssertionError("Replaced chunk must not be mutated");
                    });
                });
                awaitBlocked(mutationThread);
                plate.delete(0, 0);
                assertNotSame(original, runtime.mantle.getChunk(0, 0));
            }
            assertFalse(mutation.get(1L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1L, TimeUnit.SECONDS));
        }
    }

    private static void awaitBlocked(AtomicReference<Thread> thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
        while ((thread.get() == null || thread.get().getState() != Thread.State.BLOCKED)
                && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue("Mutation must be waiting for the chunk monitor",
                thread.get() != null && thread.get().getState() == Thread.State.BLOCKED);
    }

    @Test
    public void pressureReclaimPersistsOnlyTheOldestEligiblePlate() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("oldest-pressure-reclaim"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.timeForTest = 1_000L;
            mantle.getChunk(64, 0);
            mantle.timeForTest = 1_500L;
            mantle.getChunk(0, 0);
            mantle.timeForTest = 2_000L;
            mantle.getChunk(32, 0);
            mantle.timeForTest = 3_000L;

            assertTrue(mantle.saveOldestIdleTectonicPlate());

            assertEquals(Set.of(Mantle.key(2, 0)), runtime.regionIo.successfulWrites);
            assertEquals(2, mantle.getLoadedRegionCount());
            assertTrue(mantle.isChunkLoaded(0, 0));
            assertTrue(mantle.isChunkLoaded(32, 0));
        }
    }

    @Test
    public void pressureReclaimSkipsPinnedOldestPlateWithoutClosingIt() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("pinned-pressure-reclaim"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.timeForTest = 1_000L;
            MantleChunk<TestSection> pinned = mantle.getChunk(0, 0).use();
            try {
                mantle.timeForTest = 1_500L;
                mantle.getChunk(32, 0);
                mantle.timeForTest = 2_000L;

                assertTrue(mantle.saveOldestIdleTectonicPlate());

                assertEquals(Set.of(Mantle.key(1, 0)), runtime.regionIo.successfulWrites);
                assertFalse(pinned.isClosed());
                assertTrue(mantle.isChunkLoaded(0, 0));
            } finally {
                pinned.release();
            }
        }
    }

    @Test
    public void pressureReclaimProtectsFreshAndRecentlyTouchedPlates() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("recent-pressure-reclaim"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.timeForTest = 1_000L;
            mantle.getChunk(0, 0);
            mantle.timeForTest = 1_250L;
            assertFalse(mantle.saveOldestIdleTectonicPlate());
            mantle.timeForTest = 2_000L;
            mantle.getChunk(0, 0);
            assertFalse(mantle.saveOldestIdleTectonicPlate());
            mantle.timeForTest = 2_251L;
            assertTrue(mantle.saveOldestIdleTectonicPlate());
        }
    }

    @Test
    public void pressureReclaimWriteFailureRetainsThePlateAndDoesNotEvictOthers() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("failed-pressure-reclaim"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.timeForTest = 1_000L;
            MantleChunk<TestSection> oldest = mantle.getChunk(0, 0);
            mantle.timeForTest = 1_500L;
            mantle.getChunk(32, 0);
            mantle.timeForTest = 2_000L;
            runtime.regionIo.failWritesFor(Mantle.key(0, 0));
            try {
                assertThrows(IllegalStateException.class, mantle::saveOldestIdleTectonicPlate);
                assertFalse(oldest.isClosed());
                assertEquals(2, mantle.getLoadedRegionCount());
                assertEquals(0, runtime.regionIo.attempts(Mantle.key(1, 0)));
            } finally {
                runtime.regionIo.allowWrites();
            }
        }
    }

    @Test
    public void interruptedPressureReclaimLeavesPlatesLoaded() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("interrupted-pressure-reclaim"))) {
            TestMantle mantle = (TestMantle) runtime.mantle;
            mantle.timeForTest = 1_000L;
            mantle.getChunk(0, 0);
            mantle.timeForTest = 2_000L;
            Thread.currentThread().interrupt();
            try {
                assertFalse(mantle.saveOldestIdleTectonicPlate());
                assertEquals(1, mantle.getLoadedRegionCount());
                assertEquals(Set.of(), runtime.regionIo.successfulWrites);
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    public void missingQueriesAndRemovalsDoNotPopulateSections() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("missing-section-access"))) {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(-1, 2);
            for (int y = 0; y < 16; y++) {
                assertNull(chunk.get(3, y, 5, String.class));
                assertNull(runtime.mantle.get(-13, y, 37, String.class));
                runtime.mantle.remove(-13, y, 37, String.class);
                assertNull(chunk.get(0));
            }
        }
    }

    @Test
    public void existingSectionValuesRemainReadableAndRemovable() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("existing-section-access"))) {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(-1, 2);
            runtime.mantle.set(-13, 9, 37, "retained");
            TestSection section = chunk.get(0);

            assertEquals("retained", chunk.get(3, 9, 5, String.class));
            assertEquals("retained", runtime.mantle.get(-13, 9, 37, String.class));
            runtime.mantle.remove(-13, 9, 37, String.class);
            assertNull(chunk.get(3, 9, 5, String.class));
            assertNull(runtime.mantle.get(-13, 9, 37, String.class));
            assertSame(section, chunk.get(0));
        }
    }

    @Test
    public void queryAndRemovalBoundsKeepTheirExistingContracts() throws Exception {
        try (TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("section-access-bounds"))) {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(0, 0);
            for (int y : new int[]{Integer.MIN_VALUE, -1, 16, Integer.MAX_VALUE}) {
                assertNull(runtime.mantle.get(0, y, 0, String.class));
                runtime.mantle.remove(0, y, 0, String.class);
                assertThrows(IndexOutOfBoundsException.class, () -> chunk.get(0, y, 0, String.class));
                assertNull(chunk.get(0));
            }
        }
    }

    @Test
    public void closedChunkQueriesRemainRejectedWithoutCreatingSections() throws Exception {
        MantleChunk<TestSection> chunk = new MantleChunk<>(1, 0, 0, ADAPTER, MantleHooks.NONE);
        chunk.close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> chunk.get(0, 0, 0, String.class));

        assertEquals("Chunk is closed!", failure.getMessage());
        assertNull(chunk.get(0));
    }

    @Test
    public void failedCloseRetainsUnsavedRegionsForRetry() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("close-write-failure"));
        try {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(0, 0);
            long key = Mantle.key(0, 0);
            runtime.regionIo.failWritesFor(key);

            assertThrows(IllegalStateException.class, runtime.mantle::close);

            assertFalse(runtime.mantle.isClosed());
            assertFalse(chunk.isClosed());
            assertSame(chunk, runtime.mantle.getChunk(0, 0));
            assertEquals(0, runtime.regionIo.closeAttempts);
            assertEquals(1, runtime.regionIo.attempts(key));

            runtime.regionIo.allowWrites();
            runtime.mantle.close();

            assertTrue(runtime.mantle.isClosed());
            assertEquals(2, runtime.regionIo.attempts(key));
            assertEquals(1, runtime.regionIo.closeAttempts);
        } finally {
            runtime.close();
        }
    }

    @Test
    public void failedSaveAllRetainsUnsavedRegionsForRetry() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("save-all-write-failure"));
        try {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(0, 0);
            long key = Mantle.key(0, 0);
            runtime.regionIo.failWritesFor(key);

            assertThrows(IllegalStateException.class, runtime.mantle::saveAll);

            assertFalse(runtime.mantle.isClosed());
            assertFalse(chunk.isClosed());
            assertSame(chunk, runtime.mantle.getChunk(0, 0));

            runtime.regionIo.allowWrites();
            runtime.mantle.saveAll();

            assertEquals(2, runtime.regionIo.attempts(key));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));
        } finally {
            runtime.close();
        }
    }

    @Test
    public void failedIoCloseCanBeRetriedWithoutRewritingSavedRegions() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("close-io-failure"));
        try {
            runtime.mantle.getChunk(0, 0);
            long key = Mantle.key(0, 0);
            runtime.regionIo.failClose = true;

            assertThrows(IllegalStateException.class, runtime.mantle::close);

            assertFalse(runtime.mantle.isClosed());
            assertEquals(1, runtime.regionIo.attempts(key));
            assertEquals(1, runtime.regionIo.closeAttempts);

            runtime.regionIo.failClose = false;
            runtime.mantle.close();

            assertTrue(runtime.mantle.isClosed());
            assertEquals(1, runtime.regionIo.attempts(key));
            assertEquals(2, runtime.regionIo.closeAttempts);
        } finally {
            runtime.close();
        }
    }

    @Test
    public void requestedRegionsPersistAndUnloadWithoutTouchingOtherLoadedRegions() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("targeted-success"));
        try {
            runtime.mantle.getChunk(0, 0);
            runtime.mantle.getChunk(32, 0);
            long requested = Mantle.key(0, 0);
            long untouched = Mantle.key(1, 0);

            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(requested)));

            assertEquals(Set.of(requested), runtime.regionIo.successfulWrites);
            assertEquals(1, runtime.regionIo.attempts(requested));
            assertEquals(0, runtime.regionIo.attempts(untouched));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));
            assertTrue(runtime.mantle.isChunkLoaded(32, 0));
        } finally {
            runtime.close();
        }
    }

    @Test
    public void writeFailurePropagatesWithoutClosingOrUnloadingTheLiveRegion() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("targeted-failure"));
        try {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(0, 0);
            long requested = Mantle.key(0, 0);
            TectonicPlate<TestSection> plate = runtime.mantle.getLoadedRegions().get(requested);
            runtime.regionIo.failWritesFor(requested);

            assertThrows(IllegalStateException.class,
                    () -> runtime.mantle.saveIdleTectonicPlates(List.of(requested)));

            assertEquals(1, runtime.regionIo.attempts(requested));
            assertFalse(runtime.regionIo.successfulWrites.contains(requested));
            assertSame(plate, runtime.mantle.getLoadedRegions().get(requested));
            assertFalse(plate.isClosed());
            assertFalse(chunk.isClosed());
            assertTrue(runtime.mantle.isChunkLoaded(0, 0));
            assertSame(chunk, chunk.use());
            chunk.release();

            runtime.regionIo.allowWrites();
            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(requested)));
            assertEquals(2, runtime.regionIo.attempts(requested));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));
        } finally {
            runtime.close();
        }
    }

    @Test(timeout = 2_000L)
    public void previouslyReturnedChunkCannotEnterUseDuringSerialization() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("targeted-barrier"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            MantleChunk<TestSection> chunk = runtime.mantle.getChunk(0, 0);
            long requested = Mantle.key(0, 0);
            runtime.regionIo.blockWritesFor(requested);
            Future<?> saving = executor.submit(
                    () -> runtime.mantle.saveIdleTectonicPlates(List.of(requested)));
            assertTrue(runtime.regionIo.writeEntered.await(1L, TimeUnit.SECONDS));

            Future<?> acquiring = executor.submit(
                    () -> assertThrows(IllegalStateException.class, chunk::use));
            acquiring.get(1L, TimeUnit.SECONDS);
            runtime.regionIo.releaseBlockedWrite();
            saving.get(1L, TimeUnit.SECONDS);

            assertEquals(1, runtime.regionIo.attempts(requested));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));
        } finally {
            runtime.regionIo.releaseBlockedWrite();
            executor.shutdownNow();
            executor.awaitTermination(1L, TimeUnit.SECONDS);
            runtime.close();
        }
    }

    @Test(timeout = 2_000L)
    public void idleSaveDefersBusyRegionsWithoutBlockingOtherRegions() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("targeted-idle-save"));
        try {
            MantleChunk<TestSection> busyChunk = runtime.mantle.getChunk(0, 0).use();
            runtime.mantle.getChunk(32, 0);
            long busy = Mantle.key(0, 0);
            long idle = Mantle.key(1, 0);
            try {
                long started = System.nanoTime();
                Set<Long> deferred = runtime.mantle.saveIdleTectonicPlates(List.of(busy, idle));
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

                assertTrue(elapsedMillis < 500L);
                assertEquals(Set.of(busy), deferred);
                assertEquals(0, runtime.regionIo.attempts(busy));
                assertEquals(1, runtime.regionIo.attempts(idle));
                assertTrue(runtime.mantle.isChunkLoaded(0, 0));
                assertFalse(runtime.mantle.isChunkLoaded(32, 0));
                assertFalse(busyChunk.isClosed());
            } finally {
                busyChunk.release();
            }

            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(busy)));
            assertEquals(1, runtime.regionIo.attempts(busy));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));
        } finally {
            runtime.close();
        }
    }

    @Test(timeout = 2_000L)
    public void idleSaveDefersLockedRegionWithoutWaitingForTheOwner() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("targeted-locked-save"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        long requested = Mantle.key(0, 0);
        try {
            runtime.mantle.getChunk(0, 0);
            Future<Void> holder = executor.submit((Callable<Void>) () -> {
                runtime.hyperLock.lock(0, 0);
                try {
                    lockHeld.countDown();
                    assertTrue(releaseLock.await(1L, TimeUnit.SECONDS));
                } finally {
                    runtime.hyperLock.unlock(0, 0);
                }
                return null;
            });
            assertTrue(lockHeld.await(1L, TimeUnit.SECONDS));

            long started = System.nanoTime();
            Set<Long> deferred = runtime.mantle.saveIdleTectonicPlates(List.of(requested));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(elapsedMillis < 500L);
            assertEquals(Set.of(requested), deferred);
            assertEquals(0, runtime.regionIo.attempts(requested));
            assertTrue(runtime.mantle.isChunkLoaded(0, 0));

            releaseLock.countDown();
            holder.get(1L, TimeUnit.SECONDS);
            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(requested)));
            assertEquals(1, runtime.regionIo.attempts(requested));
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            executor.awaitTermination(1L, TimeUnit.SECONDS);
            runtime.close();
        }
    }

    @Test
    public void completedRegionLoadDoesNotRetainAClosedFuture() throws Exception {
        TestRuntime runtime = new TestRuntime(
                temporaryFolder.newFolder("completed-region-load"),
                new ImmediateRegionLoadBurst()
        );
        try {
            runtime.mantle.getChunks(0, 0, 0, 0, 2, (x, z, chunk) -> {
            });
            long regionKey = Mantle.key(0, 0);
            TectonicPlate<TestSection> original = runtime.mantle.getLoadedRegions().get(regionKey);

            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(regionKey)));

            assertTrue(original.isClosed());
            runtime.mantle.getChunks(0, 0, 0, 0, 2, (x, z, chunk) -> {
            });
            TectonicPlate<TestSection> reloaded = runtime.mantle.getLoadedRegions().get(regionKey);
            assertNotSame(original, reloaded);
            assertFalse(reloaded.isClosed());
        } finally {
            runtime.close();
        }
    }

    @Test(timeout = 3_000L)
    public void concurrentObserverCannotSeeSourceCompletionBeforePublishedLoadCompletes() throws Exception {
        PausingImmediateRegionLoadBurst burst = new PausingImmediateRegionLoadBurst();
        TestRuntime runtime = new TestRuntime(
                temporaryFolder.newFolder("concurrent-completed-region-load"),
                burst
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        long regionKey = Mantle.key(0, 0);
        TectonicPlate<TestSection> original = null;
        try {
            Future<CompletableFuture<TectonicPlate<TestSection>>> initialCall = executor.submit(
                    () -> runtime.loadRegionForTest(0, 0));
            assertTrue(burst.callbackEntered.await(1L, TimeUnit.SECONDS));
            original = runtime.mantle.getLoadedRegions().remove(regionKey);
            assertTrue(original != null);

            CompletableFuture<TectonicPlate<TestSection>> observer =
                    runtime.loadRegionForTest(0, 0);

            assertFalse(observer.isDone());
            burst.releaseCallback();
            CompletableFuture<TectonicPlate<TestSection>> initial = initialCall.get(1L, TimeUnit.SECONDS);
            assertSame(original, initial.get(1L, TimeUnit.SECONDS));
            assertSame(original, observer.get(1L, TimeUnit.SECONDS));
        } finally {
            burst.releaseCallback();
            if (original != null && !original.isClosed()) {
                runtime.mantle.getLoadedRegions().putIfAbsent(regionKey, original);
            }
            executor.shutdownNow();
            executor.awaitTermination(1L, TimeUnit.SECONDS);
            runtime.close();
        }
    }

    private static final class TestRuntime implements AutoCloseable {
        private final MultiBurstSupport burst;
        private final HyperLockSupport hyperLock;
        private final RecordingRegionIo regionIo;
        private final Mantle<TectonicPlate<TestSection>, MantleChunk<TestSection>> mantle;

        private TestRuntime(File dataFolder) {
            this(dataFolder, new MultiBurstSupport(
                    "mantle-targeted-save-test",
                    Thread.NORM_PRIORITY,
                    () -> 1,
                    ignored -> 1,
                    System::currentTimeMillis,
                    error -> {
                        throw new AssertionError(error);
                    },
                    ignored -> {
                    },
                    ignored -> {
                    },
                    1_000L
            ));
        }

        private TestRuntime(File dataFolder, MultiBurstSupport burst) {
            this.burst = burst;
            this.hyperLock = new HyperLockSupport();
            this.regionIo = new RecordingRegionIo();
            this.mantle = new TestMantle(
                    dataFolder,
                    hyperLock,
                    burst,
                    regionIo
            );
        }

        @Override
        public void close() {
            regionIo.allowWrites();
            mantle.close();
            burst.shutdownNow();
        }

        private CompletableFuture<TectonicPlate<TestSection>> loadRegionForTest(int x, int z) {
            return ((TestMantle) mantle).loadRegionForTest(x, z);
        }
    }

    private static class ImmediateRegionLoadBurst extends MultiBurstSupport {
        protected ImmediateRegionLoadBurst() {
            super(
                    "mantle-immediate-load-test",
                    Thread.NORM_PRIORITY,
                    () -> 1,
                    ignored -> 1,
                    System::currentTimeMillis,
                    error -> {
                        throw new AssertionError(error);
                    },
                    ignored -> {
                    },
                    ignored -> {
                    },
                    1_000L
            );
        }

        @Override
        public <T> CompletableFuture<T> completableFuture(Callable<T> operation) {
            try {
                return CompletableFuture.completedFuture(operation.call());
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
    }

    private static final class PausingImmediateRegionLoadBurst extends ImmediateRegionLoadBurst {
        private final AtomicBoolean pauseNextCallback = new AtomicBoolean(true);
        private final CountDownLatch callbackEntered = new CountDownLatch(1);
        private final CountDownLatch allowCallback = new CountDownLatch(1);

        @Override
        public <T> CompletableFuture<T> completableFuture(Callable<T> operation) {
            try {
                return new PausingCompletedFuture<>(operation.call(), this);
            } catch (Exception exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }

        private void awaitCallback() {
            if (!pauseNextCallback.compareAndSet(true, false)) {
                return;
            }
            callbackEntered.countDown();
            try {
                if (!allowCallback.await(1L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release region-load completion callback");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting to release region-load completion callback", exception);
            }
        }

        private void releaseCallback() {
            allowCallback.countDown();
        }
    }

    private static final class PausingCompletedFuture<T> extends CompletableFuture<T> {
        private final PausingImmediateRegionLoadBurst burst;

        private PausingCompletedFuture(T value, PausingImmediateRegionLoadBurst burst) {
            this.burst = burst;
            complete(value);
        }

        @Override
        public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            burst.awaitCallback();
            return super.whenComplete(action);
        }
    }

    private static final class TestMantle
            extends art.arcane.volmlib.util.mantle.runtime.Mantle<TestSection> {
        private long timeForTest = Long.MIN_VALUE;

        private TestMantle(File dataFolder,
                           HyperLockSupport hyperLock,
                           MultiBurstSupport burst,
                           RecordingRegionIo regionIo) {
            super(dataFolder, 16, 32, hyperLock, burst,
                    regionIo, ADAPTER, MantleHooks.NONE);
        }

        private CompletableFuture<TectonicPlate<TestSection>> loadRegionForTest(int x, int z) {
            return getSafe(x, z);
        }

        @Override
        protected long nowMillis() {
            return timeForTest == Long.MIN_VALUE ? super.nowMillis() : timeForTest;
        }
    }

    private static final class RecordingRegionIo implements Mantle.RegionIO<TectonicPlate<TestSection>> {
        private final Map<Long, AtomicInteger> writeAttempts = new ConcurrentHashMap<>();
        private final Set<Long> successfulWrites = ConcurrentHashMap.newKeySet();
        private volatile Long failingRegion;
        private volatile Long blockedRegion;
        private boolean failClose;
        private int closeAttempts;
        private volatile CountDownLatch writeEntered = new CountDownLatch(0);
        private volatile CountDownLatch allowWrite = new CountDownLatch(0);

        @Override
        public TectonicPlate<TestSection> read(String name) {
            throw new IllegalStateException("Unexpected targeted-save test read for " + name);
        }

        @Override
        public void write(String name, TectonicPlate<TestSection> region) throws IOException {
            long key = Mantle.key(region.getX(), region.getZ());
            writeAttempts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            if (Long.valueOf(key).equals(failingRegion)) {
                throw new IOException("Simulated targeted region write failure");
            }
            if (Long.valueOf(key).equals(blockedRegion)) {
                CountDownLatch entered = writeEntered;
                CountDownLatch allowed = allowWrite;
                entered.countDown();
                try {
                    if (!allowed.await(1L, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release targeted region write");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting to release targeted region write", error);
                }
            }
            successfulWrites.add(key);
        }

        @Override
        public void close() throws IOException {
            closeAttempts++;
            if (failClose) {
                throw new IOException("Simulated region IO close failure");
            }
        }

        private int attempts(long key) {
            AtomicInteger attempts = writeAttempts.get(key);
            return attempts == null ? 0 : attempts.get();
        }

        private void failWritesFor(long key) {
            failingRegion = key;
        }

        private void allowWrites() {
            failingRegion = null;
            failClose = false;
            releaseBlockedWrite();
        }

        private void blockWritesFor(long key) {
            blockedRegion = key;
            writeEntered = new CountDownLatch(1);
            allowWrite = new CountDownLatch(1);
        }

        private void releaseBlockedWrite() {
            allowWrite.countDown();
            blockedRegion = null;
        }
    }

    private static final class TestAdapter implements MantleDataAdapter<TestSection> {
        @Override
        public TestSection createSection() {
            return new TestSection();
        }

        @Override
        public TestSection readSection(CountingDataInputStream input) {
            return new TestSection();
        }

        @Override
        public void writeSection(TestSection section, DataOutputStream output) {
        }

        @Override
        public void trimSection(TestSection section) {
        }

        @Override
        public boolean isSectionEmpty(TestSection section) {
            return section.values.isEmpty();
        }

        @Override
        public Class<?> classifyValue(Object value) {
            return value.getClass();
        }

        @Override
        public <T> void set(TestSection section, int x, int y, int z, Class<?> type, T value) {
            section.values.put(type, value);
        }

        @Override
        public <T> void remove(TestSection section, int x, int y, int z, Class<T> type) {
            section.values.remove(type);
        }

        @Override
        public <T> T get(TestSection section, int x, int y, int z, Class<T> type) {
            return type.cast(section.values.get(type));
        }

        @Override
        public <T> void iterate(TestSection section, Class<T> type,
                                Consumer4<Integer, Integer, Integer, T> iterator) {
        }

        @Override
        public boolean hasSlice(TestSection section, Class<?> type) {
            return section.values.containsKey(type);
        }

        @Override
        public void deleteSlice(TestSection section, Class<?> type) {
            section.values.remove(type);
        }
    }

    private static final class TestSection {
        private final Map<Class<?>, Object> values = new ConcurrentHashMap<>();
    }

    @Test
    public void repeatedAccessReturnsTheLivePlateAndNeverResurrectsAnUnloadedOne() throws Exception {
        TestRuntime runtime = new TestRuntime(temporaryFolder.newFolder("guarded-access"));
        try {
            MantleChunk<TestSection> first = runtime.mantle.getChunk(0, 0);
            long requested = Mantle.key(0, 0);
            TectonicPlate<TestSection> plate = runtime.mantle.getLoadedRegions().get(requested);

            assertSame(first, runtime.mantle.getChunk(0, 0));
            assertSame(plate, runtime.mantle.getLoadedRegions().get(requested));

            assertEquals(Set.of(), runtime.mantle.saveIdleTectonicPlates(List.of(requested)));
            assertFalse(runtime.mantle.isChunkLoaded(0, 0));

            MantleChunk<TestSection> reloaded = runtime.mantle.getChunk(0, 0);
            assertNotSame(first, reloaded);
            assertNotSame(plate, runtime.mantle.getLoadedRegions().get(requested));
        } finally {
            runtime.close();
        }
    }
}
