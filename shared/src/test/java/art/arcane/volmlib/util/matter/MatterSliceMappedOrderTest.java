package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.volmlib.util.hunk.storage.ArrayHunk;
import art.arcane.volmlib.util.hunk.storage.MappedHunk;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MatterSliceMappedOrderTest {
    @Test
    public void mappedSlicesVisitAndWriteCellsInCoordinateOrder() throws IOException {
        MappedStrings mapped = new MappedStrings(16, 24, 16);
        ArrayStrings reference = new ArrayStrings(16, 24, 16);
        Random random = new Random(1337L);
        for (int index = 0; index < 700; index++) {
            int x = random.nextInt(16);
            int y = random.nextInt(24);
            int z = random.nextInt(16);
            String value = "value-" + random.nextInt(40);
            mapped.set(x, y, z, value);
            reference.set(x, y, z, value);
        }
        List<String> expected = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 24; y++) {
                for (int z = 0; z < 16; z++) {
                    String value = reference.getRaw(x, y, z);
                    if (value != null) {
                        expected.add(x + "," + y + "," + z + "=" + value);
                    }
                }
            }
        }
        List<String> visited = new ArrayList<>();

        mapped.iterateSync((Integer x, Integer y, Integer z, String value) -> visited.add(x + "," + y + "," + z + "=" + value));

        assertEquals(expected, visited);
        assertArrayEquals(bytes(reference), bytes(mapped));
    }

    private static byte[] bytes(MatterSlice<String> slice) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        slice.write(new DataOutputStream(output));
        return output.toByteArray();
    }

    private static final class MappedStrings extends MappedHunk<String> implements MatterSlice<String> {
        private MappedStrings(int width, int height, int depth) {
            super(width, height, depth);
        }

        @Override
        public String get(int x, int y, int z) {
            return MatterSlice.super.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, String value) {
            MatterSlice.super.set(x, y, z, value);
        }

        @Override
        public MappedStrings iterateSync(Consumer4<Integer, Integer, Integer, String> consumer) {
            MatterSlice.super.iterateSync(consumer);
            return this;
        }

        @Override
        public MappedStrings iterateSyncIO(Consumer4IO<Integer, Integer, Integer, String> consumer) throws IOException {
            MatterSlice.super.iterateSyncIO(consumer);
            return this;
        }

        @Override
        public void empty(String value) {
            clear();
        }

        @Override
        public boolean isEmpty() {
            return getEntryCount() == 0;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public Palette<String> getGlobalPalette() {
            return null;
        }

        @Override
        public void writeNode(String value, DataOutputStream output) throws IOException {
            output.writeUTF(value);
        }

        @Override
        public String readNode(DataInputStream input) throws IOException {
            return input.readUTF();
        }

        @Override
        public <W> MatterWriter<W, String> writeInto(Class<W> mediumType) {
            return null;
        }

        @Override
        public <W> MatterReader<W, String> readFrom(Class<W> mediumType) {
            return null;
        }
    }

    private static final class ArrayStrings extends ArrayHunk<String> implements MatterSlice<String> {
        private ArrayStrings(int width, int height, int depth) {
            super(width, height, depth);
        }

        @Override
        public String get(int x, int y, int z) {
            return MatterSlice.super.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, String value) {
            MatterSlice.super.set(x, y, z, value);
        }

        @Override
        public ArrayStrings iterateSync(Consumer4<Integer, Integer, Integer, String> consumer) {
            MatterSlice.super.iterateSync(consumer);
            return this;
        }

        @Override
        public ArrayStrings iterateSyncIO(Consumer4IO<Integer, Integer, Integer, String> consumer) throws IOException {
            MatterSlice.super.iterateSyncIO(consumer);
            return this;
        }

        @Override
        public boolean isMapped() {
            return true;
        }

        @Override
        public int getEntryCount() {
            int count = 0;
            for (Object value : (Object[]) getData()) {
                if (value != null) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public void empty(String value) {
            fill(null);
        }

        @Override
        public boolean isEmpty() {
            return getEntryCount() == 0;
        }

        @Override
        public Class<String> getType() {
            return String.class;
        }

        @Override
        public Palette<String> getGlobalPalette() {
            return null;
        }

        @Override
        public void writeNode(String value, DataOutputStream output) throws IOException {
            output.writeUTF(value);
        }

        @Override
        public String readNode(DataInputStream input) throws IOException {
            return input.readUTF();
        }

        @Override
        public <W> MatterWriter<W, String> writeInto(Class<W> mediumType) {
            return null;
        }

        @Override
        public <W> MatterReader<W, String> readFrom(Class<W> mediumType) {
            return null;
        }
    }
}
