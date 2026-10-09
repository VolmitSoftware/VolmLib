package art.arcane.volmlib.util.nbt.io;

import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.ByteTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.DoubleTag;
import art.arcane.volmlib.util.nbt.tag.EndTag;
import art.arcane.volmlib.util.nbt.tag.FloatTag;
import art.arcane.volmlib.util.nbt.tag.IntArrayTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.LongArrayTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.ShortTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class BoundedNBTReader {
    private final DataInputStream input;
    private final Limits limits;
    private long remaining;
    private int tags;

    private BoundedNBTReader(InputStream input, Limits limits) {
        this.input = new DataInputStream(Objects.requireNonNull(input));
        this.limits = Objects.requireNonNull(limits);
        remaining = limits.maxBytes();
    }

    public static NamedTag read(InputStream input, Limits limits) throws IOException {
        return new BoundedNBTReader(input, limits).readRoot();
    }

    private NamedTag readRoot() throws IOException {
        int type = unsignedByte();
        validateType(type, false);
        String name = string();
        Tag<?> value = tag(type, 0);
        if (input.read() != -1) { throw new IOException("Trailing data after NBT root"); }
        return new NamedTag(name, value);
    }

    private Tag<?> tag(int type, int depth) throws IOException {
        if (depth > limits.maxDepth()) { throw new IOException("NBT nesting exceeds its limit"); }
        if (tags >= limits.maxTags()) { throw new IOException("NBT tag count exceeds its limit"); }
        tags++;
        return switch (type) {
            case 1 -> { take(1); yield new ByteTag(input.readByte()); }
            case 2 -> { take(2); yield new ShortTag(input.readShort()); }
            case 3 -> { take(4); yield new IntTag(input.readInt()); }
            case 4 -> { take(8); yield new LongTag(input.readLong()); }
            case 5 -> { take(4); yield new FloatTag(input.readFloat()); }
            case 6 -> { take(8); yield new DoubleTag(input.readDouble()); }
            case 7 -> byteArray();
            case 8 -> new StringTag(string());
            case 9 -> list(depth);
            case 10 -> compound(depth);
            case 11 -> intArray();
            case 12 -> longArray();
            default -> throw new IOException("Invalid NBT tag type: " + type);
        };
    }

    private CompoundTag compound(int depth) throws IOException {
        CompoundTag result = new CompoundTag();
        int type;
        while ((type = unsignedByte()) != 0) {
            validateType(type, false);
            if (tags >= limits.maxTags()) { throw new IOException("NBT tag count exceeds its limit"); }
            String name = string();
            if (result.containsKey(name)) { throw new IOException("Duplicate NBT compound key"); }
            result.put(name, tag(type, depth + 1));
        }
        return result;
    }

    private ListTag<?> list(int depth) throws IOException {
        int type = unsignedByte();
        validateType(type, true);
        int length = length();
        if (type == 0 && length != 0) { throw new IOException("Nonempty NBT list cannot use END type"); }
        if (length > limits.maxTags() - tags) { throw new IOException("NBT list exceeds remaining tag budget"); }
        ListTag<?> result = ListTag.createUnchecked(typeClass(type));
        for (int index = 0; index < length; index++) { result.addUnchecked(tag(type, depth + 1)); }
        return result;
    }

    private ByteArrayTag byteArray() throws IOException {
        int length = length();
        take(length);
        byte[] values = new byte[length];
        input.readFully(values);
        return new ByteArrayTag(values);
    }

    private IntArrayTag intArray() throws IOException {
        int length = length();
        take((long) length * Integer.BYTES);
        int[] values = new int[length];
        for (int index = 0; index < length; index++) { values[index] = input.readInt(); }
        return new IntArrayTag(values);
    }

    private LongArrayTag longArray() throws IOException {
        int length = length();
        take((long) length * Long.BYTES);
        long[] values = new long[length];
        for (int index = 0; index < length; index++) { values[index] = input.readLong(); }
        return new LongArrayTag(values);
    }

    private int length() throws IOException {
        take(4);
        int length = input.readInt();
        if (length < 0 || length > limits.maxArrayLength()) { throw new IOException("NBT collection length exceeds its limit"); }
        return length;
    }

    private String string() throws IOException {
        take(2);
        int length = input.readUnsignedShort();
        if (length > limits.maxStringBytes()) { throw new IOException("NBT string exceeds its byte limit"); }
        take(length);
        byte[] encoded = new byte[length + 2];
        encoded[0] = (byte) (length >>> 8);
        encoded[1] = (byte) length;
        input.readFully(encoded, 2, length);
        return new DataInputStream(new ByteArrayInputStream(encoded)).readUTF();
    }

    private int unsignedByte() throws IOException {
        take(1);
        return input.readUnsignedByte();
    }

    private void take(long bytes) throws IOException {
        if (bytes > remaining) { throw new IOException("NBT bytes exceed their limit"); }
        remaining -= bytes;
    }

    private static void validateType(int type, boolean allowEnd) throws IOException {
        if (type < (allowEnd ? 0 : 1) || type > 12) { throw new IOException("Invalid NBT tag type: " + type); }
    }

    private static Class<?> typeClass(int type) {
        return switch (type) {
            case 0 -> EndTag.class;
            case 1 -> ByteTag.class;
            case 2 -> ShortTag.class;
            case 3 -> IntTag.class;
            case 4 -> LongTag.class;
            case 5 -> FloatTag.class;
            case 6 -> DoubleTag.class;
            case 7 -> ByteArrayTag.class;
            case 8 -> StringTag.class;
            case 9 -> ListTag.class;
            case 10 -> CompoundTag.class;
            case 11 -> IntArrayTag.class;
            case 12 -> LongArrayTag.class;
            default -> throw new IllegalArgumentException("Invalid NBT tag type");
        };
    }

    public record Limits(long maxBytes, int maxDepth, int maxTags, int maxArrayLength, int maxStringBytes) {
        public Limits {
            if (maxBytes < 1 || maxDepth < 0 || maxDepth > 512 || maxTags < 1 || maxArrayLength < 0
                    || maxStringBytes < 0 || maxStringBytes > 65535) {
                throw new IllegalArgumentException("Invalid NBT read limits");
            }
        }
    }
}
