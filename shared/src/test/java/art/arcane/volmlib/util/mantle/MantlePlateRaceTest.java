package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Plates saved by a targeted eviction (pregen backpressure, idle saves) or the unloader while another
 * thread is about to use them.
 */
public class MantlePlateRaceTest {
    private static final int WORLD_HEIGHT = 64;
    private static final MantleDataAdapter<StringSection> ADAPTER = new StringAdapter();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 5_000L)
    public void sequentialVisitOfAPlateEvictedRightAfterItsLookupDefersTheEviction() throws Exception {
        assertVisitDefersEvictionTriggeredAtLookup(1);
    }

    @Test(timeout = 5_000L)
    public void parallelVisitOfAPlateEvictedRightAfterItsLookupDefersTheEviction() throws Exception {
        assertVisitDefersEvictionTriggeredAtLookup(4);
    }

    private void assertVisitDefersEvictionTriggeredAtLookup(int parallelism) throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("visit-lookup-" + parallelism))) {
            fixture.mantle.set(5, 3, 7, "kept");
            long id = Mantle.key(0, 0);
            AtomicReference<Set<Long>> deferred = new AtomicReference<>();
            fixture.mantle.afterMarkUsed.set(() -> deferred.set(fixture.saveIdleElsewhere(id)));

            List<MantleChunk<StringSection>> visited = new ArrayList<>();
            fixture.mantle.getChunks(0, 1, 0, 1, parallelism, (x, z, chunk) -> {
                assertFalse(chunk.isClosed());
                synchronized (visited) {
                    visited.add(chunk);
                }
            });

            assertEquals(Set.of(id), deferred.get());
            assertEquals(4, visited.size());
            assertEquals("kept", fixture.mantle.get(5, 3, 7, String.class));
            assertEquals(Set.of(), fixture.mantle.saveIdleTectonicPlates(List.of(id)));
            assertEquals("kept", fixture.mantle.get(5, 3, 7, String.class));
        }
    }

    @Test(timeout = 5_000L)
    public void visitOfAPlateSavedBeforeItIsPinnedReloadsThePersistedPlate() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("visit-reload"))) {
            fixture.mantle.set(5, 3, 7, "kept");
            long id = Mantle.key(0, 0);
            TectonicPlate<StringSection> original = fixture.mantle.getLoadedRegion(0, 0);
            AtomicReference<Set<Long>> deferred = new AtomicReference<>();
            fixture.mantle.afterLoadedLookup.set(() -> deferred.set(fixture.saveIdleElsewhere(id)));

            AtomicInteger visits = new AtomicInteger();
            AtomicReference<String> seen = new AtomicReference<>();
            fixture.mantle.getChunks(0, 0, 0, 0, 1, (x, z, chunk) -> {
                visits.incrementAndGet();
                seen.set(chunk.get(5, 3, 7, String.class));
            });

            assertEquals(Set.of(), deferred.get());
            assertTrue(original.isClosed());
            assertNotSame(original, fixture.mantle.getLoadedRegion(0, 0));
            assertEquals(1, visits.get());
            assertEquals("kept", seen.get());
        }
    }

    @Test(timeout = 5_000L)
    public void evictionRequestedWhileAVisitIsRunningWaitsForTheVisit() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("visit-running"))) {
            fixture.mantle.getChunk(0, 0);
            long id = Mantle.key(0, 0);
            AtomicReference<Set<Long>> deferred = new AtomicReference<>();
            AtomicInteger visits = new AtomicInteger();
            fixture.mantle.getChunks(0, 1, 0, 1, 1, (x, z, chunk) -> {
                if (visits.getAndIncrement() == 0) {
                    deferred.set(fixture.saveIdleElsewhere(id));
                }
                assertFalse(chunk.isClosed());
                chunk.flag(MantleFlag.REAL, true);
            });

            assertEquals(Set.of(id), deferred.get());
            assertEquals(4, visits.get());
            assertTrue(fixture.mantle.hasFlag(1, 1, MantleFlag.REAL));
        }
    }

    @Test(timeout = 5_000L)
    public void chunkLookupOfAPlateEvictedRightAfterItsLookupDefersTheEviction() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("chunk-lookup"))) {
            MantleChunk<StringSection> original = fixture.mantle.getChunk(0, 0);
            long id = Mantle.key(0, 0);
            AtomicReference<Set<Long>> deferred = new AtomicReference<>();
            fixture.mantle.afterMarkUsed.set(() -> deferred.set(fixture.saveIdleElsewhere(id)));

            MantleChunk<StringSection> chunk = fixture.mantle.getChunk(0, 0);

            assertEquals(Set.of(id), deferred.get());
            assertSame(original, chunk);
            assertFalse(chunk.isClosed());
        }
    }

    @Test(timeout = 5_000L)
    public void usedChunkIsNeverClosedByAnEvictionAtItsLookup() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("use-lookup"))) {
            fixture.mantle.set(1, 2, 3, "kept");
            long id = Mantle.key(0, 0);
            AtomicReference<Set<Long>> deferred = new AtomicReference<>();
            fixture.mantle.afterMarkUsed.set(() -> deferred.set(fixture.saveIdleElsewhere(id)));

            MantleChunk<StringSection> chunk = fixture.mantle.useChunk(0, 0);
            try {
                assertEquals(Set.of(id), deferred.get());
                assertEquals(Set.of(id), fixture.saveIdleElsewhere(id));
                assertFalse(chunk.isClosed());
                chunk.flag(MantleFlag.REAL, true);
                assertEquals("kept", chunk.get(1, 2, 3, String.class));
            } finally {
                chunk.release();
            }
            assertEquals(Set.of(), fixture.mantle.saveIdleTectonicPlates(List.of(id)));
            assertTrue(fixture.mantle.hasFlag(0, 0, MantleFlag.REAL));
        }
    }

    @Test(timeout = 5_000L)
    public void chunkHandedOutWithoutAUseIsClosedByALaterEviction() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("unused-chunk"))) {
            MantleChunk<StringSection> chunk = fixture.mantle.getChunk(0, 0);
            assertEquals(Set.of(), fixture.mantle.saveIdleTectonicPlates(List.of(Mantle.key(0, 0))));

            assertThrows(IllegalStateException.class, chunk::use);
            MantleChunk<StringSection> reloaded = fixture.mantle.useChunk(0, 0);
            try {
                assertNotSame(chunk, reloaded);
                assertFalse(reloaded.isClosed());
            } finally {
                reloaded.release();
            }
        }
    }

    @Test(timeout = 5_000L)
    public void failedEvictionOfABusyPlateNeverClosesItsChunks() throws Exception {
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("busy-plate"))) {
            for (int x = 0; x < 4; x++) {
                fixture.mantle.getChunk(x, 0);
            }
            MantleChunk<StringSection> busy = fixture.mantle.useChunk(3, 0);
            try {
                TectonicPlate<StringSection> plate = fixture.mantle.getLoadedRegion(0, 0);
                fixture.clock.addAndGet(10_000L);
                assertFalse(fixture.mantle.saveOldestIdleTectonicPlate());
                assertFalse(plate.isClosed());
                for (int x = 0; x < 4; x++) {
                    assertFalse(plate.get(x, 0).isClosed());
                }
                assertEquals(0, fixture.regionIo.writes.get());
            } finally {
                busy.release();
            }
            fixture.clock.addAndGet(10_000L);
            assertTrue(fixture.mantle.saveOldestIdleTectonicPlate());
            assertNull(fixture.mantle.getLoadedRegion(0, 0));
        }
    }

    @Test(timeout = 20_000L)
    public void concurrentEvictionNeverFailsAccessOrLosesWrites() throws Exception {
        int workers = 6;
        long runNanos = TimeUnit.MILLISECONDS.toNanos(900L);
        ExecutorService executor = Executors.newFixedThreadPool(workers + 2);
        try (Fixture fixture = new Fixture(temporaryFolder.newFolder("stress"))) {
            Map<Long, String> expected = new ConcurrentHashMap<>();
            AtomicBoolean stop = new AtomicBoolean();
            AtomicLong evictions = new AtomicLong();
            List<Future<?>> tasks = new ArrayList<>();
            tasks.add(executor.submit(() -> {
                while (!stop.get()) {
                    fixture.clock.addAndGet(1_000L);
                    if (fixture.mantle.saveOldestIdleTectonicPlate()) {
                        evictions.incrementAndGet();
                    }
                }
                return null;
            }));
            tasks.add(executor.submit(() -> {
                while (!stop.get()) {
                    fixture.mantle.trim(0L);
                    fixture.mantle.unloadTectonicPlate(0);
                    Thread.sleep(2L);
                }
                return null;
            }));
            for (int worker = 0; worker < workers; worker++) {
                int seed = worker;
                tasks.add(executor.submit(() -> {
                    SplittableRandom random = new SplittableRandom(seed);
                    long deadline = System.nanoTime() + runNanos;
                    int serial = 0;
                    while (System.nanoTime() < deadline && !stop.get()) {
                        int chunkX = random.nextInt(-40, 40);
                        int chunkZ = random.nextInt(-40, 40);
                        int y = random.nextInt(WORLD_HEIGHT);
                        int ownY = (seed << 3) + random.nextInt(8);
                        switch (random.nextInt(5)) {
                            case 0 -> {
                                String value = seed + ":" + serial++;
                                int blockX = (chunkX << 4) + random.nextInt(16);
                                int blockZ = (chunkZ << 4) + random.nextInt(16);
                                fixture.mantle.set(blockX, ownY, blockZ, value);
                                expected.put(blockKey(blockX, ownY, blockZ), value);
                            }
                            case 1 -> {
                                MantleChunk<StringSection> chunk = fixture.mantle.useChunk(chunkX, chunkZ);
                                try {
                                    String value = seed + ":" + serial++;
                                    int localX = random.nextInt(16);
                                    int localZ = random.nextInt(16);
                                    fixture.setThrough(chunk, localX, ownY, localZ, value);
                                    expected.put(blockKey((chunkX << 4) + localX, ownY, (chunkZ << 4) + localZ), value);
                                } finally {
                                    chunk.release();
                                }
                            }
                            case 2 -> {
                                List<MantleChunk<StringSection>> window = new ArrayList<>();
                                fixture.mantle.getChunks(chunkX - 2, chunkX + 2, chunkZ - 2, chunkZ + 2,
                                        1 + random.nextInt(3), (x, z, chunk) -> {
                                            chunk.use();
                                            synchronized (window) {
                                                window.add(chunk);
                                            }
                                        });
                                try {
                                    for (MantleChunk<StringSection> chunk : window) {
                                        assertFalse(chunk.isClosed());
                                        chunk.flag(MantleFlag.REAL, true);
                                    }
                                } finally {
                                    for (MantleChunk<StringSection> chunk : window) {
                                        chunk.release();
                                    }
                                }
                            }
                            case 3 -> fixture.mantle.hasFlag(chunkX, chunkZ, MantleFlag.REAL);
                            default -> {
                                int blockX = (chunkX << 4) + random.nextInt(16);
                                int blockZ = (chunkZ << 4) + random.nextInt(16);
                                fixture.mantle.get(blockX, y, blockZ, String.class);
                            }
                        }
                    }
                    return null;
                }));
            }
            try {
                for (int index = 2; index < tasks.size(); index++) {
                    tasks.get(index).get(10L, TimeUnit.SECONDS);
                }
            } finally {
                stop.set(true);
                for (Future<?> task : tasks) {
                    task.get(10L, TimeUnit.SECONDS);
                }
            }

            assertTrue("the evictor must have saved plates while the workers ran", evictions.get() > 0);
            fixture.mantle.saveAll();
            for (Map.Entry<Long, String> entry : expected.entrySet()) {
                long key = entry.getKey();
                int x = (int) (key >> 40);
                int y = (int) ((key >> 20) & 0xFFFFF) - 0x80000;
                int z = (int) (key & 0xFFFFF) - 0x80000;
                assertEquals("value at " + x + "," + y + "," + z, entry.getValue(),
                        fixture.mantle.get(x, y, z, String.class));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x << 40) | ((long) (y + 0x80000) << 20) | (z + 0x80000);
    }

    private static final class Fixture implements AutoCloseable {
        private final AtomicLong clock = new AtomicLong(1_000_000L);
        private final MultiBurstSupport burst;
        private final FileRegionIo regionIo;
        private final RaceMantle mantle;
        private final ExecutorService evictor = Executors.newSingleThreadExecutor();

        private Fixture(File folder) {
            this.burst = new MultiBurstSupport(
                    "mantle-plate-race-test",
                    Thread.NORM_PRIORITY,
                    () -> 4,
                    ignored -> 4,
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
            this.regionIo = new FileRegionIo(folder);
            this.mantle = new RaceMantle(folder, new HyperLockSupport(), burst, regionIo, clock);
        }

        private Set<Long> saveIdleElsewhere(long id) {
            try {
                return evictor.submit(() -> mantle.saveIdleTectonicPlates(List.of(id))).get(2L, TimeUnit.SECONDS);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }

        private void setThrough(MantleChunk<StringSection> chunk, int x, int y, int z, String value) {
            ADAPTER.set(chunk.getOrCreate(y >> 4), x, y & 15, z, String.class, value);
        }

        @Override
        public void close() throws Exception {
            evictor.shutdownNow();
            evictor.awaitTermination(1L, TimeUnit.SECONDS);
            mantle.close();
            burst.shutdownNow();
        }
    }

    private static final class RaceMantle extends art.arcane.volmlib.util.mantle.runtime.Mantle<StringSection> {
        private final AtomicLong clock;
        private final AtomicReference<Runnable> afterMarkUsed = new AtomicReference<>();
        private final AtomicReference<Runnable> afterLoadedLookup = new AtomicReference<>();

        private RaceMantle(File folder, HyperLockSupport hyperLock, MultiBurstSupport burst,
                           FileRegionIo regionIo, AtomicLong clock) {
            super(folder, WORLD_HEIGHT, 64, hyperLock, burst, regionIo, ADAPTER, MantleHooks.NONE);
            this.clock = clock;
        }

        @Override
        protected long nowMillis() {
            return clock.get();
        }

        @Override
        public TectonicPlate<StringSection> getLoadedRegion(int x, int z) {
            TectonicPlate<StringSection> region = super.getLoadedRegion(x, z);
            if (region != null) {
                Runnable hook = afterLoadedLookup.getAndSet(null);
                if (hook != null) {
                    hook.run();
                }
            }
            return region;
        }

        @Override
        protected void markRegionUsed(int x, int z, TectonicPlate<StringSection> region) {
            super.markRegionUsed(x, z, region);
            Runnable hook = afterMarkUsed.getAndSet(null);
            if (hook != null) {
                hook.run();
            }
        }
    }

    private static final class FileRegionIo implements Mantle.RegionIO<TectonicPlate<StringSection>> {
        private final File folder;
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicLong temporaries = new AtomicLong();

        private FileRegionIo(File folder) {
            this.folder = folder;
        }

        @Override
        public TectonicPlate<StringSection> read(String name) throws IOException {
            try (CountingDataInputStream input = CountingDataInputStream.wrap(
                    new BufferedInputStream(new FileInputStream(new File(folder, name))))) {
                return TectonicPlate.read(WORLD_HEIGHT, input, true, ADAPTER, MantleHooks.NONE);
            }
        }

        @Override
        public void write(String name, TectonicPlate<StringSection> region) throws IOException {
            File target = new File(folder, name);
            File temporary = new File(folder, name + "." + temporaries.incrementAndGet() + ".tmp");
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
                region.write(output);
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            writes.incrementAndGet();
        }

        @Override
        public void close() {
        }
    }

    private static final class StringSection {
        private final Map<Integer, String> values = new ConcurrentHashMap<>();
    }

    private static final class StringAdapter implements MantleDataAdapter<StringSection> {
        @Override
        public StringSection createSection() {
            return new StringSection();
        }

        @Override
        public StringSection readSection(CountingDataInputStream input) throws IOException {
            StringSection section = new StringSection();
            int count = input.readInt();
            for (int i = 0; i < count; i++) {
                section.values.put(input.readInt(), input.readUTF());
            }
            return section;
        }

        @Override
        public void writeSection(StringSection section, DataOutputStream output) throws IOException {
            Map<Integer, String> snapshot = Map.copyOf(section.values);
            output.writeInt(snapshot.size());
            for (Map.Entry<Integer, String> entry : snapshot.entrySet()) {
                output.writeInt(entry.getKey());
                output.writeUTF(entry.getValue());
            }
        }

        @Override
        public void trimSection(StringSection section) {
        }

        @Override
        public boolean isSectionEmpty(StringSection section) {
            return section.values.isEmpty();
        }

        @Override
        public Class<?> classifyValue(Object value) {
            return value.getClass();
        }

        @Override
        public <T> void set(StringSection section, int x, int y, int z, Class<?> type, T value) {
            section.values.put(index(x, y, z), (String) value);
        }

        @Override
        public <T> void remove(StringSection section, int x, int y, int z, Class<T> type) {
            section.values.remove(index(x, y, z));
        }

        @Override
        public <T> T get(StringSection section, int x, int y, int z, Class<T> type) {
            return type.cast(section.values.get(index(x, y, z)));
        }

        @Override
        public <T> void iterate(StringSection section, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
            for (Map.Entry<Integer, String> entry : section.values.entrySet()) {
                int index = entry.getKey();
                iterator.accept(index >> 8, (index >> 4) & 15, index & 15, type.cast(entry.getValue()));
            }
        }

        @Override
        public boolean hasSlice(StringSection section, Class<?> type) {
            return !section.values.isEmpty();
        }

        @Override
        public void deleteSlice(StringSection section, Class<?> type) {
            section.values.clear();
        }

        private static int index(int x, int y, int z) {
            return ((x & 15) << 8) | ((y & 15) << 4) | (z & 15);
        }
    }
}
