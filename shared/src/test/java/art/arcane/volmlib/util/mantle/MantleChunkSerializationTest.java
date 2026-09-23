package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.io.CountingDataInputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MantleChunkSerializationTest {
    @Test
    public void emptyAndSparseSectionsKeepTheirLengthFramedBytes() throws Exception {
        for (int[] values : new int[][]{{0, 0, 0, 0}, {0, 13, 0, 27}, {13, 0, 0, 0}, {0, 0, 0, 27}}) {
            TestChunk chunk = new TestChunk();
            for (int index = 0; index < values.length; index++) {
                chunk.getOrCreate(index)[0] = values[index];
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            chunk.write(new DataOutputStream(bytes));

            assertArrayEquals(chunk.expectedBytes(values), bytes.toByteArray());
            CountingDataInputStream input = CountingDataInputStream.wrap(new ByteArrayInputStream(bytes.toByteArray()));
            TestChunk restored = new TestChunk(input);
            assertEquals(-1, input.read());
            for (int index = 0; index < values.length; index++) {
                if (values[index] == 0) {
                    assertNull(restored.get(index));
                } else {
                    assertEquals(values[index], restored.get(index)[0]);
                }
            }
        }
    }

    private static final class TestChunk extends MantleChunkSupport<int[]> {
        private TestChunk() {
            super(4, 3, 7);
        }

        private TestChunk(CountingDataInputStream input) throws IOException {
            super(TectonicPlate.CURRENT, 4, input);
        }

        private byte[] expectedBytes(int[] values) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(3);
            output.writeByte(7);
            output.writeByte(4);
            writeFlags(output);
            for (int value : values) {
                output.writeInt(value == 0 ? 0 : Integer.BYTES);
                if (value != 0) {
                    output.writeInt(value);
                }
            }
            return bytes.toByteArray();
        }

        @Override
        protected int[] createSection() {
            return new int[1];
        }

        @Override
        protected int[] readSection(CountingDataInputStream input) throws IOException {
            return new int[]{input.readInt()};
        }

        @Override
        protected void writeSection(int[] section, DataOutputStream output) throws IOException {
            output.writeInt(section[0]);
        }

        @Override
        protected void trimSection(int[] section) {
        }

        @Override
        protected boolean isSectionEmpty(int[] section) {
            return section[0] == 0;
        }
    }
}
