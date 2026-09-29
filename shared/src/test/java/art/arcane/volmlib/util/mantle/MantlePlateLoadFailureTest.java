package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
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
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MantlePlateLoadFailureTest {
    private static final int WORLD_HEIGHT = 64;
    private static final MantleDataAdapter<StringSection> ADAPTER = new StringAdapter();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 5_000L)
    public void interruptedPlateReadFailsTheAccessAndKeepsThePersistedPlate() throws Exception {
        assertTransientReadFailureKeepsThePlate(new ClosedByInterruptException());
    }

    @Test(timeout = 5_000L)
    public void closedMantleDuringAPlateReadFailsTheAccessAndKeepsThePersistedPlate() throws Exception {
        assertTransientReadFailureKeepsThePlate(new MantleClosedException("Tectonic Plate is closed!"));
    }

    @Test(timeout = 5_000L)
    public void jvmErrorDuringAPlateReadFailsTheAccessAndKeepsThePersistedPlate() throws Exception {
        assertTransientReadFailureKeepsThePlate(new OutOfMemoryError("plate buffer"));
    }

    @Test(timeout = 5_000L)
    public void interruptedReaderThreadFailsTheAccessAndKeepsThePersistedPlate() throws Exception {
        File folder = persistedPlate("interrupted-thread");
        FileRegionIo regionIo = new FileRegionIo(folder);
        regionIo.failure.set(new IOException("Stream closed"));
        try (Fixture fixture = new Fixture(folder, regionIo)) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(RuntimeException.class, () -> fixture.mantle.get(5, 3, 7, String.class));
            } finally {
                Thread.interrupted();
            }

            assertNull(regionIo.failure.get());
            assertNull(fixture.mantle.getLoadedRegion(0, 0));
            assertEquals("kept", fixture.mantle.get(5, 3, 7, String.class));
        }
    }

    @Test(timeout = 5_000L)
    public void corruptPlateStillFallsBackToAFreshPlate() throws Exception {
        File folder = persistedPlate("corrupt");
        FileRegionIo regionIo = new FileRegionIo(folder);
        regionIo.failure.set(new EOFException("truncated plate"));
        try (Fixture fixture = new Fixture(folder, regionIo)) {
            assertNull(fixture.mantle.get(5, 3, 7, String.class));
        }
    }

    private void assertTransientReadFailureKeepsThePlate(Throwable failure) throws Exception {
        File folder = persistedPlate("transient-" + failure.getClass().getSimpleName());
        File plate = Mantle.fileForRegion(folder, 0, 0);
        long persistedLength = plate.length();
        FileRegionIo regionIo = new FileRegionIo(folder);
        regionIo.failure.set(failure);
        try (Fixture fixture = new Fixture(folder, regionIo)) {
            Throwable thrown = assertThrows(Throwable.class, () -> fixture.mantle.get(5, 3, 7, String.class));

            assertTrue(thrown.toString(), causedBy(thrown, failure));
            assertNull(fixture.mantle.getLoadedRegion(0, 0));
            assertEquals("kept", fixture.mantle.get(5, 3, 7, String.class));
        }
        assertEquals(persistedLength, plate.length());
    }

    private File persistedPlate(String name) throws Exception {
        File folder = temporaryFolder.newFolder(name);
        try (Fixture fixture = new Fixture(folder, new FileRegionIo(folder))) {
            fixture.mantle.set(5, 3, 7, "kept");
        }
        assertTrue(Mantle.fileForRegion(folder, 0, 0).length() > 0L);
        return folder;
    }

    private static boolean causedBy(Throwable thrown, Throwable expected) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current == expected) {
                return true;
            }
        }
        return false;
    }

    private static final class Fixture implements AutoCloseable {
        private final MultiBurstSupport burst;
        private final art.arcane.volmlib.util.mantle.runtime.Mantle<StringSection> mantle;

        private Fixture(File folder, FileRegionIo regionIo) {
            this.burst = new MultiBurstSupport(
                    "mantle-plate-load-failure-test",
                    Thread.NORM_PRIORITY,
                    () -> 2,
                    ignored -> 2,
                    System::currentTimeMillis,
                    error -> {
                    },
                    ignored -> {
                    },
                    ignored -> {
                    },
                    1_000L
            );
            this.mantle = new art.arcane.volmlib.util.mantle.runtime.Mantle<>(folder, WORLD_HEIGHT, 64,
                    new HyperLockSupport(), burst, regionIo, ADAPTER, MantleHooks.NONE);
        }

        @Override
        public void close() {
            mantle.close();
            burst.shutdownNow();
        }
    }

    private static final class FileRegionIo implements Mantle.RegionIO<TectonicPlate<StringSection>> {
        private final File folder;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private FileRegionIo(File folder) {
            this.folder = folder;
        }

        @Override
        public TectonicPlate<StringSection> read(String name) throws Exception {
            Throwable pending = failure.getAndSet(null);
            if (pending instanceof Exception exception) {
                throw exception;
            }
            if (pending instanceof Error error) {
                throw error;
            }
            try (CountingDataInputStream input = CountingDataInputStream.wrap(
                    new BufferedInputStream(new FileInputStream(new File(folder, name))))) {
                return TectonicPlate.read(WORLD_HEIGHT, input, true, ADAPTER, MantleHooks.NONE);
            }
        }

        @Override
        public void write(String name, TectonicPlate<StringSection> region) throws IOException {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new FileOutputStream(new File(folder, name))))) {
                region.write(output);
            }
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
