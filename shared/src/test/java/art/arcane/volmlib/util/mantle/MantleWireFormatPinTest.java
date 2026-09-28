package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Pins the persisted plate bytes: the digest was captured from the encoder before its bulk rewrite, so any
 * change to the wire layout of sections, slices, palettes or packed data fails here.
 */
public class MantleWireFormatPinTest {
    private static final int SECTIONS = 8;
    private static final String PLATE_DIGEST = "7330f79cbd7be920b5405420de377389afee1a222b3bfb4b2dc79acc3f29f1b5";

    @Test
    public void plateBytesMatchThePinnedEncodingAndRoundTrip() throws Exception {
        TectonicPlate<Matter> plate = new TectonicPlate<>(SECTIONS * 16, 5, -3, ADAPTER, MantleHooks.NONE);
        Random random = new Random(0x5EEDL);
        int[] paletteSizes = {1, 2, 3, 5, 15, 16, 17, 31, 63, 64, 65, 120, 255, 256, 257, 700};
        double[] densities = {0.0005D, 0.01D, 0.2D, 0.7D, 1.0D};
        int chunkIndex = 0;
        for (int paletteSize : paletteSizes) {
            for (double density : densities) {
                var chunk = plate.getOrCreate(chunkIndex & 31, chunkIndex >> 5);
                chunkIndex++;
                for (int section = 0; section < SECTIONS; section++) {
                    if (random.nextInt(5) == 0) {
                        continue;
                    }
                    Matter matter = chunk.getOrCreate(section);
                    matter.getHeader().setCreatedAt(1_700_000_000_000L + chunkIndex * 16L + section);
                    fill(matter, random, paletteSize, density, section);
                }
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        plate.write(new DataOutputStream(bytes));
        byte[] encoded = bytes.toByteArray();

        CountingDataInputStream input = CountingDataInputStream.readAhead(new ByteArrayInputStream(encoded));
        TectonicPlate<Matter> restored = TectonicPlate.read(SECTIONS * 16, input, true, ADAPTER, MantleHooks.NONE);
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        restored.write(new DataOutputStream(again));

        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(again.toByteArray())));
        assertEquals(PLATE_DIGEST, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
    }

    private static void fill(Matter matter, Random random, int paletteSize, double density, int section) {
        switch (section % 3) {
            case 0 -> {
                MatterSlice<Integer> slice = matter.slice(Integer.class);
                writeCells(random, density, (x, y, z) -> slice.set(x, y, z, random.nextInt(paletteSize) * 7 - 3));
                overwrite(random, (x, y, z) -> slice.set(x, y, z, random.nextBoolean() ? null : -1));
            }
            case 1 -> {
                MatterSlice<String> slice = matter.slice(String.class);
                writeCells(random, density, (x, y, z) -> slice.set(x, y, z, "p" + random.nextInt(paletteSize)));
                overwrite(random, (x, y, z) -> slice.set(x, y, z, null));
            }
            default -> {
                MatterSlice<Long> slice = matter.slice(Long.class);
                boolean aligned = random.nextBoolean();
                writeCells(random, density, (x, y, z) -> {
                    if (!aligned || (((y << 8) | (z << 4) | x) & 0x333) == 0) {
                        slice.set(x, y, z, (long) random.nextInt(paletteSize) << 33);
                    }
                });
            }
        }
    }

    private static void writeCells(Random random, double density, CellWriter writer) {
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (random.nextDouble() < density) {
                        writer.write(x, y, z);
                    }
                }
            }
        }
    }

    private static void overwrite(Random random, CellWriter writer) {
        int count = random.nextInt(40);
        for (int i = 0; i < count; i++) {
            writer.write(random.nextInt(16), random.nextInt(16), random.nextInt(16));
        }
    }

    @FunctionalInterface
    private interface CellWriter {
        void write(int x, int y, int z);
    }

    private static final MantleDataAdapter<Matter> ADAPTER = new MantleDataAdapter<>() {
        @Override
        public Matter createSection() {
            return new IrisMatter(16, 16, 16);
        }

        @Override
        public Matter readSection(CountingDataInputStream din) throws IOException {
            return Matter.readDin(din);
        }

        @Override
        public void writeSection(Matter section, DataOutputStream dos) throws IOException {
            section.writeTrimmedDos(dos);
        }

        @Override
        public void trimSection(Matter section) {
            section.trimSlices();
        }

        @Override
        public boolean isSectionEmpty(Matter section) {
            return section.getSliceMap().isEmpty();
        }

        @Override
        public Class<?> classifyValue(Object value) {
            return value.getClass();
        }

        @Override
        public <T> void set(Matter section, int x, int y, int z, Class<?> type, T value) {
            section.<T>slice(type).set(x, y, z, value);
        }

        @Override
        public <T> void remove(Matter section, int x, int y, int z, Class<T> type) {
            MatterSlice<T> slice = section.getSlice(type);
            if (slice != null) {
                slice.set(x, y, z, null);
            }
        }

        @Override
        public <T> T get(Matter section, int x, int y, int z, Class<T> type) {
            MatterSlice<T> slice = section.getSlice(type);
            return slice == null ? null : slice.get(x, y, z);
        }

        @Override
        public <T> void iterate(Matter section, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
            MatterSlice<T> slice = section.getSlice(type);
            if (slice != null) {
                slice.iterateSync(iterator);
            }
        }

        @Override
        public boolean hasSlice(Matter section, Class<?> type) {
            return section.hasSlice(type);
        }

        @Override
        public void deleteSlice(Matter section, Class<?> type) {
            section.deleteSlice(type);
        }
    };
}
