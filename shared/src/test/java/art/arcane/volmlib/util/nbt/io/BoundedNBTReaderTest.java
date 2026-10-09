package art.arcane.volmlib.util.nbt.io;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BoundedNBTReaderTest {
    private static final BoundedNBTReader.Limits LIMITS = new BoundedNBTReader.Limits(4096, 8, 100, 100, 100);

    @Test
    public void readsAllPrimitiveTypesArraysAndModifiedUtf() throws Exception {
        byte[] bytes = document(output -> {
            output.writeByte(1); output.writeUTF("byte"); output.writeByte(-3);
            output.writeByte(2); output.writeUTF("short"); output.writeShort(123);
            output.writeByte(3); output.writeUTF("int"); output.writeInt(456);
            output.writeByte(4); output.writeUTF("long"); output.writeLong(789);
            output.writeByte(5); output.writeUTF("float"); output.writeFloat(1.5F);
            output.writeByte(6); output.writeUTF("double"); output.writeDouble(2.5);
            output.writeByte(7); output.writeUTF("bytes"); output.writeInt(2); output.write(new byte[]{1, 2});
            output.writeByte(8); output.writeUTF("text"); output.writeUTF("A\u0000漢😀");
            output.writeByte(11); output.writeUTF("ints"); output.writeInt(1); output.writeInt(7);
            output.writeByte(12); output.writeUTF("longs"); output.writeInt(1); output.writeLong(8);
        });
        NamedTag named = read(bytes, LIMITS);
        assertEquals("root", named.getName());
        CompoundTag root = (CompoundTag) named.getTag();
        assertEquals(-3, root.getByte("byte"));
        assertEquals(123, root.getShort("short"));
        assertEquals(456, root.getInt("int"));
        assertEquals(789L, root.getLong("long"));
        assertEquals(1.5F, root.getFloat("float"), 0);
        assertEquals(2.5, root.getDouble("double"), 0);
        assertArrayEquals(new byte[]{1, 2}, root.getByteArray("bytes"));
        assertEquals("A\u0000漢😀", root.getString("text"));
        assertArrayEquals(new int[]{7}, root.getIntArray("ints"));
        assertArrayEquals(new long[]{8}, root.getLongArray("longs"));
        assertEquals(root, read(bytes, new BoundedNBTReader.Limits(bytes.length, 8, 100, 100, 100)).getTag());
        assertThrows(IOException.class, () -> read(bytes, new BoundedNBTReader.Limits(bytes.length - 1, 8, 100, 100, 100)));
    }

    @Test
    public void acceptsTypedAndEmptyEndLists() throws Exception {
        byte[] bytes = document(output -> {
            output.writeByte(9); output.writeUTF("values"); output.writeByte(3); output.writeInt(2);
            output.writeInt(12); output.writeInt(13);
            output.writeByte(9); output.writeUTF("empty"); output.writeByte(0); output.writeInt(0);
        });
        CompoundTag root = (CompoundTag) read(bytes, LIMITS).getTag();
        assertEquals(2, root.getListTag("values").size());
        assertEquals(IntTag.class, root.getListTag("values").getTypeClass());
        assertEquals(0, root.getListTag("empty").size());
    }

    @Test
    public void rejectsDuplicateKeysInvalidTypesAndTrailingBytes() throws Exception {
        byte[] duplicate = document(output -> {
            output.writeByte(1); output.writeUTF("same"); output.writeByte(1);
            output.writeByte(1); output.writeUTF("same"); output.writeByte(2);
        });
        assertThrows(IOException.class, () -> read(duplicate, LIMITS));
        assertThrows(IOException.class, () -> read(new byte[]{0}, LIMITS));
        assertThrows(IOException.class, () -> read(new byte[]{13}, LIMITS));
        byte[] empty = document(output -> { });
        assertThrows(IOException.class, () -> read(Arrays.copyOf(empty, empty.length + 1), LIMITS));
        assertThrows(IOException.class, () -> read(Arrays.copyOf(empty, empty.length - 1), LIMITS));
    }

    @Test
    public void rejectsMalformedListsBeforeReadingElements() throws Exception {
        for (int type : new int[]{0, 13, 255}) {
            byte[] bytes = document(output -> {
                output.writeByte(9); output.writeUTF("bad"); output.writeByte(type); output.writeInt(1);
            });
            assertThrows(IOException.class, () -> read(bytes, LIMITS));
        }
        byte[] negative = document(output -> {
            output.writeByte(9); output.writeUTF("bad"); output.writeByte(1); output.writeInt(-1);
        });
        assertThrows(IOException.class, () -> read(negative, LIMITS));
    }

    @Test
    public void boundsArrayLengthAndRemainingByteBudgetBeforeAllocation() throws Exception {
        for (int type : new int[]{7, 11, 12}) {
            byte[] huge = document(output -> {
                output.writeByte(type); output.writeUTF("array"); output.writeInt(Integer.MAX_VALUE);
            });
            assertThrows(IOException.class, () -> read(huge, LIMITS));
            byte[] negative = document(output -> {
                output.writeByte(type); output.writeUTF("array"); output.writeInt(-1);
            });
            assertThrows(IOException.class, () -> read(negative, LIMITS));
        }
        byte[] overBytes = document(output -> {
            output.writeByte(12); output.writeUTF("array"); output.writeInt(100);
        });
        assertThrows(IOException.class, () -> read(overBytes, new BoundedNBTReader.Limits(100, 8, 100, 100, 100)));
    }

    @Test
    public void boundsCompoundDepthAndAggregateListTagCount() throws Exception {
        byte[] nested = document(output -> {
            output.writeByte(10); output.writeUTF("child");
            output.writeByte(8); output.writeUTF("text"); output.writeUTF("value");
            output.writeByte(0);
        });
        assertThrows(IOException.class, () -> read(nested, new BoundedNBTReader.Limits(4096, 1, 100, 100, 100)));
        read(nested, new BoundedNBTReader.Limits(4096, 2, 3, 100, 100));
        assertThrows(IOException.class, () -> read(nested, new BoundedNBTReader.Limits(4096, 2, 2, 100, 100)));
        byte[] list = document(output -> {
            output.writeByte(9); output.writeUTF("many"); output.writeByte(10); output.writeInt(99);
        });
        assertThrows(IOException.class, () -> read(list, LIMITS));
    }

    @Test
    public void boundsStringsAndRejectsMalformedModifiedUtf() throws Exception {
        byte[] text = document(output -> {
            output.writeByte(8); output.writeUTF("text"); output.writeUTF("long value");
        });
        assertThrows(IOException.class, () -> read(text, new BoundedNBTReader.Limits(4096, 8, 100, 100, 5)));
        byte[] malformed = document(output -> {
            output.writeByte(8); output.writeUTF("text"); output.writeShort(1); output.writeByte(0xC0);
        });
        assertThrows(IOException.class, () -> read(malformed, LIMITS));
    }

    @Test
    public void validatesLimitConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedNBTReader.Limits(0, 8, 100, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNBTReader.Limits(100, 513, 100, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNBTReader.Limits(100, 8, 0, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNBTReader.Limits(100, 8, 100, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNBTReader.Limits(100, 8, 100, 100, 65536));
    }

    private static NamedTag read(byte[] bytes, BoundedNBTReader.Limits limits) throws IOException {
        return BoundedNBTReader.read(new ByteArrayInputStream(bytes), limits);
    }

    private static byte[] document(Writer writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(10);
        output.writeUTF("root");
        writer.write(output);
        output.writeByte(0);
        return bytes.toByteArray();
    }

    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }
}
