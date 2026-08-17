package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
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
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MantleTargetedSaveTest {
    private static final MantleDataAdapter<TestSection> ADAPTER = new TestAdapter();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
    }

    private static final class RecordingRegionIo implements Mantle.RegionIO<TectonicPlate<TestSection>> {
        private final Map<Long, AtomicInteger> writeAttempts = new ConcurrentHashMap<>();
        private final Set<Long> successfulWrites = ConcurrentHashMap.newKeySet();
        private volatile Long failingRegion;
        private volatile Long blockedRegion;
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
        public void close() {
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
}
