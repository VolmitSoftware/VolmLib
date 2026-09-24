package art.arcane.volmlib.util.hunk.bits;

import art.arcane.volmlib.util.data.Varint;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class DataContainerMemoryTest {
    private static final Writable<Integer> INTEGERS = new Writable<>() {
        @Override
        public Integer readNodeData(DataInputStream input) throws IOException {
            return input.readInt();
        }

        @Override
        public void writeNodeData(DataOutputStream output, Integer value) throws IOException {
            output.writeInt(value);
        }
    };

    @Test
    public void selectedPositionCopiesMatchScalarReadsBeforeAndAfterExpansion() {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        int[] positions = {0, 1, 4, 16, 64, 256, 1024, 4095, 0};
        Integer[] copied = new Integer[positions.length];
        for (int position = 0; position < 4096; position++) {
            if ((position & 0x333) == 0) {
                container.set(position, position + 1);
            }
        }
        for (int pass = 0; pass < 2; pass++) {
            Arrays.fill(copied, -1);
            container.copyTo(positions, copied);
            for (int index = 0; index < positions.length; index++) {
                assertEquals(container.get(positions[index]), copied[index]);
            }
            container.set(1, 8192);
            container.set(4095, 4096);
            container.set(64, null);
        }
    }

    @Test
    public void invalidPositionCopiesDoNotPartiallyOverwriteDestination() {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        container.set(0, 71);
        Integer[] destination = {1, 2};
        assertThrows(IllegalArgumentException.class, () -> container.copyTo(new int[]{0, 4096}, destination));
        assertArrayEquals(new Integer[]{1, 2}, destination);
        assertThrows(IllegalArgumentException.class, () -> container.copyTo(new int[]{-1, 0}, destination));
        assertThrows(IllegalArgumentException.class, () -> container.copyTo(new int[]{0}, destination));
        container.copyTo(new int[0], new Integer[0]);
        container.set(0, 72);
        assertEquals(Integer.valueOf(72), container.get(0));
    }

    @Test
    public void denseWidthsKeepOriginalSerializedPaletteLayout() throws Exception {
        for (int cardinality : new int[]{0, 1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 255}) {
            for (int length : new int[]{4093, 4096}) {
                DataContainer<Integer> container = new DataContainer<>(INTEGERS, length);
                Integer[] values = new Integer[length];
                List<Integer> insertionOrder = new ArrayList<>();
                for (int id = 0; id < cardinality; id++) {
                    insertionOrder.add(1000 + id);
                }
                for (int position = 0; position < length; position++) {
                    if (cardinality > 0 && (position < cardinality || position % 5 != 0)) {
                        values[position] = insertionOrder.get(position % cardinality);
                        container.set(position, values[position]);
                    }
                }
                int memoryBits = cardinality == 0 && length == 4096 ? 1 : Math.max(3, 32 - Integer.numberOfLeadingZeros(cardinality));
                assertEquals(memoryBits, data(container).getBits());
                int physicalLength = cardinality == 0 && length == 4096 ? 64 : length;
                assertEquals(physicalLength, data(container).getSize());
                assertEquals((physicalLength + 64 / memoryBits - 1) / (64 / memoryBits), data(container).getRaw().length());
                byte[] expected = originalWireBytes(values, insertionOrder);
                assertArrayEquals(expected, container.write());
                ByteArrayOutputStream framed = new ByteArrayOutputStream();
                framed.write(expected);
                framed.write(0x7b);
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(framed.toByteArray()));
                DataContainer<Integer> loaded = new DataContainer<>(input, INTEGERS);
                assertEquals(0x7b, input.readUnsignedByte());
                assertEquals(memoryBits, data(loaded).getBits());
                assertEquals(physicalLength, data(loaded).getSize());
                assertValues(values, loaded);
                assertArrayEquals(expected, loaded.write());
            }
        }
    }

    @Test
    public void trimRemovesUnusedIdsInOriginalOrderAndAllowsGrowthAfterLoad() throws Exception {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        Integer[] values = new Integer[4096];
        List<Integer> insertionOrder = new ArrayList<>();
        for (int value = 0; value < 32; value++) {
            insertionOrder.add(value);
            container.set(value, value);
            values[value] = value;
        }
        for (int position = 0; position < 32; position++) {
            if (position != 17 && position != 29) {
                values[position] = null;
                container.set(position, null);
            }
        }
        assertArrayEquals(originalWireBytes(values, insertionOrder), container.write());
        assertEquals(3, data(container).getBits());
        DataContainer<Integer> loaded = new DataContainer<>(
                new DataInputStream(new ByteArrayInputStream(container.write())), INTEGERS);
        insertionOrder = new ArrayList<>(List.of(17, 29));
        for (int value = 100; value < 132; value++) {
            loaded.set(value, value);
            values[value] = value;
            insertionOrder.add(value);
        }
        assertValues(values, loaded);
        assertArrayEquals(originalWireBytes(values, insertionOrder), loaded.write());
        for (int position = 0; position < values.length; position++) {
            loaded.set(position, null);
        }
        assertTrue(loaded.isEmptyData());
        assertArrayEquals(originalWireBytes(new Integer[4096], List.of()), loaded.write());
        assertEquals(3, data(loaded).getBits());
    }

    @Test(timeout = 20_000L)
    public void concurrentPaletteGrowthAndSerializationPreserveEveryCompletedWrite() throws Exception {
        int workers = 8;
        int positionsPerWorker = 64;
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, workers * positionsPerWorker);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers + 1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workers; worker++) {
                int firstPosition = worker * positionsPerWorker;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int pass = 0; pass < 4; pass++) {
                        for (int offset = 0; offset < positionsPerWorker; offset++) {
                            int position = firstPosition + offset;
                            container.set(position, pass * 1000 + position);
                        }
                    }
                    return null;
                }));
            }
            futures.add(executor.submit(() -> {
                start.await();
                for (int snapshot = 0; snapshot < 32; snapshot++) {
                    byte[] encoded = container.write();
                    DataContainer<Integer> loaded = new DataContainer<>(
                            new DataInputStream(new ByteArrayInputStream(encoded)), INTEGERS);
                    assertArrayEquals(encoded, loaded.write());
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15L, TimeUnit.SECONDS);
            }
            for (int position = 0; position < workers * positionsPerWorker; position++) {
                assertEquals(Integer.valueOf(3000 + position), container.get(position));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void alignedEntriesKeepTheirFullIndicesAndOriginalWireBytes() throws Exception {
        for (int cardinality : new int[]{0, 1, 2, 3, 7, 16, 64}) {
            DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
            Integer[] values = new Integer[4096];
            List<Integer> insertionOrder = new ArrayList<>();
            for (int cell = 0; cell < 64 && cardinality > 0; cell++) {
                int position = alignedPosition(63 - cell);
                Integer value = 100 + cell % cardinality;
                container.set(position, value);
                values[position] = value;
                if (!insertionOrder.contains(value)) {
                    insertionOrder.add(value);
                }
            }
            assertEquals(64, data(container).getSize());
            assertValues(values, container);
            assertIteration(values, container);
            assertEquals(cardinality == 0, container.isEmptyData());
            byte[] expected = originalWireBytes(values, insertionOrder);
            assertArrayEquals(expected, container.write());
            DataContainer<Integer> loaded = new DataContainer<>(
                    new DataInputStream(new ByteArrayInputStream(expected)), INTEGERS);
            assertEquals(64, data(loaded).getSize());
            assertValues(values, loaded);
            assertIteration(values, loaded);
            assertArrayEquals(expected, loaded.write());
            loaded.set(4095, 500);
            values[4095] = 500;
            insertionOrder.add(500);
            assertEquals(4096, data(loaded).getSize());
            assertValues(values, loaded);
            assertArrayEquals(originalWireBytes(values, insertionOrder), loaded.write());
        }
    }

    @Test
    public void nonAlignedNullWritesStayCompactAndValuesPromoteWithoutLosingEntries() throws Exception {
        for (int nonAligned : new int[]{1, 2, 3, 16, 32, 256, 512, 4095}) {
            DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
            Integer[] values = new Integer[4096];
            container.set(0, 10);
            values[0] = 10;
            container.set(3276, 20);
            values[3276] = 20;
            for (int position = 0; position < 4096; position++) {
                if ((position & 0x333) != 0) {
                    container.set(position, null);
                    assertNull(container.get(position));
                }
            }
            assertEquals(64, data(container).getSize());
            container.set(nonAligned, 10);
            values[nonAligned] = 10;
            assertEquals(4096, data(container).getSize());
            assertValues(values, container);
            assertIteration(values, container);
            byte[] expected = originalWireBytes(values, List.of(10, 20));
            assertArrayEquals(expected, container.write());
            DataContainer<Integer> loaded = new DataContainer<>(
                    new DataInputStream(new ByteArrayInputStream(expected)), INTEGERS);
            assertEquals(4096, data(loaded).getSize());
            assertEquals(3, data(loaded).getBits());
            assertValues(values, loaded);
            assertArrayEquals(expected, loaded.write());
        }
    }

    @Test
    public void compactTrimRetainsPositionOrderAndRegrowsItsPalette() throws Exception {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        Integer[] values = new Integer[4096];
        List<Integer> insertionOrder = new ArrayList<>();
        for (int cell = 0; cell < 64; cell++) {
            int position = alignedPosition(cell);
            container.set(position, cell);
            values[position] = cell;
            insertionOrder.add(cell);
        }
        for (int cell = 0; cell < 64; cell++) {
            if (cell != 3 && cell != 61) {
                container.set(alignedPosition(cell), null);
                values[alignedPosition(cell)] = null;
            }
        }
        assertArrayEquals(originalWireBytes(values, insertionOrder), container.write());
        assertEquals(2, data(container).getBits());
        assertEquals(64, data(container).getSize());
        insertionOrder = new ArrayList<>(List.of(3, 61));
        for (int cell = 0; cell < 32; cell++) {
            int position = alignedPosition(cell);
            container.set(position, 1000 + cell);
            values[position] = 1000 + cell;
            insertionOrder.add(1000 + cell);
        }
        assertArrayEquals(originalWireBytes(values, insertionOrder), container.write());
        assertValues(values, container);
        assertIteration(values, container);
        assertEquals(64, data(container).getSize());
        for (int position = 0; position < 4096; position++) {
            container.set(position, null);
        }
        assertTrue(container.isEmptyData());
        assertArrayEquals(originalWireBytes(new Integer[4096], List.of()), container.write());
        assertEquals(1, data(container).getBits());
        assertEquals(1, data(container).getRaw().length());
    }

    @Test
    public void invalidIndicesCannotAliasCompactPositionsOrPromoteStorage() throws Exception {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        container.set(0, 7);
        for (int position : new int[]{Integer.MIN_VALUE, -4096, -1, 4096, 4097, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> container.get(position));
            assertThrows(IllegalArgumentException.class, () -> container.set(position, null));
            assertThrows(IllegalArgumentException.class, () -> container.set(position, 9));
        }
        assertEquals(Integer.valueOf(7), container.get(0));
        assertEquals(64, data(container).getSize());
        container.set(4095, 9);
        for (int position : new int[]{Integer.MIN_VALUE, -1, 4096, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> container.get(position));
            assertThrows(IllegalArgumentException.class, () -> container.set(position, null));
        }
        assertEquals(Integer.valueOf(9), container.get(4095));
    }

    @Test(timeout = 20_000L)
    public void promotionPreservesConcurrentAlignedWritesReadsAndSnapshots() throws Exception {
        DataContainer<Integer> container = new DataContainer<>(INTEGERS, 4096);
        for (int cell = 0; cell < 64; cell++) {
            container.set(alignedPosition(cell), cell + 1);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> {
                start.await();
                for (int position = 0; position < 4096; position++) {
                    if ((position & 0x333) != 0) {
                        container.set(position, 1);
                    }
                }
                return null;
            }));
            futures.add(executor.submit(() -> {
                start.await();
                for (int pass = 0; pass < 32; pass++) {
                    for (int cell = 0; cell < 64; cell++) {
                        container.set(alignedPosition(cell), cell + 1);
                    }
                }
                return null;
            }));
            futures.add(executor.submit(() -> {
                start.await();
                for (int pass = 0; pass < 32; pass++) {
                    for (int cell = 0; cell < 64; cell++) {
                        assertEquals(Integer.valueOf(cell + 1), container.get(alignedPosition(cell)));
                    }
                }
                return null;
            }));
            futures.add(executor.submit(() -> {
                start.await();
                for (int snapshot = 0; snapshot < 16; snapshot++) {
                    byte[] encoded = container.write();
                    DataContainer<Integer> loaded = new DataContainer<>(
                            new DataInputStream(new ByteArrayInputStream(encoded)), INTEGERS);
                    assertArrayEquals(encoded, loaded.write());
                    assertFalse(loaded.isEmptyData());
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15L, TimeUnit.SECONDS);
            }
            for (int position = 0; position < 4096; position++) {
                int cell = ((position & 12) >>> 2) | ((position & 192) >>> 4) | ((position & 3072) >>> 6);
                assertEquals(Integer.valueOf((position & 0x333) == 0 ? cell + 1 : 1), container.get(position));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 10_000L)
    public void serializationCannotRenumberPaletteDuringMaterialLookup() throws Exception {
        for (boolean keepThird : new boolean[]{false, true}) {
            DataContainer<Integer> container = new DataContainer<>(INTEGERS, 8);
            container.set(0, 100);
            container.set(1, 200);
            if (keepThird) {
                container.set(2, 300);
            }
            container.set(0, null);
            PausedReadBits paused = new PausedReadBits(data(container));
            Field field = DataContainer.class.getDeclaredField("data");
            field.setAccessible(true);
            field.set(container, paused);
            CountDownLatch writerStarted = new CountDownLatch(1);
            CountDownLatch writerFinished = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> readResult = executor.submit(() -> {
                    paused.reader = Thread.currentThread();
                    return container.get(1);
                });
                assertTrue(paused.captured.await(2L, TimeUnit.SECONDS));
                Future<byte[]> writeResult = executor.submit(() -> {
                    writerStarted.countDown();
                    try {
                        return container.write();
                    } finally {
                        writerFinished.countDown();
                    }
                });
                assertTrue(writerStarted.await(2L, TimeUnit.SECONDS));
                assertFalse(writerFinished.await(100L, TimeUnit.MILLISECONDS));
                paused.proceed.countDown();
                assertEquals(Integer.valueOf(200), readResult.get(2L, TimeUnit.SECONDS));
                DataContainer<Integer> loaded = new DataContainer<>(
                        new DataInputStream(new ByteArrayInputStream(writeResult.get(2L, TimeUnit.SECONDS))), INTEGERS);
                assertEquals(Integer.valueOf(200), loaded.get(1));
                assertEquals(keepThird ? Integer.valueOf(300) : null, loaded.get(2));
            } finally {
                paused.proceed.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
            }
        }
    }

    private static final class PausedReadBits extends DataBits {
        private final CountDownLatch captured = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);
        private volatile Thread reader;

        private PausedReadBits(DataBits original) {
            super(original.getBits(), original.getSize(), original.getRaw());
        }

        @Override
        public int getUnchecked(int position) {
            int id = super.getUnchecked(position);
            if (Thread.currentThread() == reader) {
                captured.countDown();
                try {
                    if (!proceed.await(2L, TimeUnit.SECONDS)) {
                        throw new AssertionError("Material lookup did not resume");
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            }
            return id;
        }
    }

    private static int alignedPosition(int cell) {
        return (cell % 4) * 4 + ((cell / 4) % 4) * 64 + (cell / 16) * 1024;
    }

    private static void assertIteration(Integer[] values, DataContainer<Integer> container) throws IOException {
        List<Integer> expectedPositions = new ArrayList<>();
        List<Integer> expectedValues = new ArrayList<>();
        for (int position = 0; position < values.length; position++) {
            if (values[position] != null) {
                expectedPositions.add(position);
                expectedValues.add(values[position]);
            }
        }
        List<Integer> positions = new ArrayList<>();
        List<Integer> actualValues = new ArrayList<>();
        container.iteratePresent((position, value) -> {
            positions.add(position);
            actualValues.add(value);
        });
        assertEquals(expectedPositions, positions);
        assertEquals(expectedValues, actualValues);
        positions.clear();
        actualValues.clear();
        container.iteratePresentIO((position, value) -> {
            positions.add(position);
            actualValues.add(value);
        });
        assertEquals(expectedPositions, positions);
        assertEquals(expectedValues, actualValues);
    }

    private static DataBits data(DataContainer<?> container) throws Exception {
        Field field = DataContainer.class.getDeclaredField("data");
        field.setAccessible(true);
        return (DataBits) field.get(container);
    }

    private static void assertValues(Integer[] expected, DataContainer<Integer> actual) {
        assertEquals(expected.length, actual.size());
        for (int position = 0; position < expected.length; position++) {
            assertEquals(expected[position], actual.get(position));
        }
    }

    private static byte[] originalWireBytes(Integer[] values, List<Integer> insertionOrder) throws IOException {
        Set<Integer> present = new HashSet<>(Arrays.asList(values));
        List<Integer> palette = new ArrayList<>();
        for (Integer value : insertionOrder) {
            if (present.contains(value)) {
                palette.add(value);
            }
        }
        int bits = Math.max(3, 32 - Integer.numberOfLeadingZeros(palette.size()));
        DataBits original = new DataBits(bits, values.length);
        for (int position = 0; position < values.length; position++) {
            if (values[position] != null) {
                original.set(position, palette.indexOf(values[position]) + 1);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        Varint.writeUnsignedVarInt(values.length, output);
        Varint.writeUnsignedVarInt(palette.size(), output);
        for (Integer value : palette) {
            output.writeInt(value);
        }
        original.write(output);
        return bytes.toByteArray();
    }
}
