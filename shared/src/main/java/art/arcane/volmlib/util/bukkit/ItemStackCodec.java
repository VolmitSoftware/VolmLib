package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class ItemStackCodec {
    public static final int MAX_ENCODED_LENGTH = 131072;

    private static final String PREFIX = "vitem1:";
    private static final int MAGIC = 0x5649544D;
    private static final int MAX_BYTES = 98298;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 8192;
    private static final int MAX_COLLECTION = 4096;
    private static final Set<String> ALIASES = Set.of(
            "ItemStack", "ItemMeta", "Color", "PotionEffect", "Firework", "Pattern", "Vector", "BlockVector",
            "BoundingBox", "org.bukkit.Location", "org.bukkit.attribute.AttributeModifier", "PlayerProfile",
            "SpawnRule", "org.bukkit.block.spawner.SpawnRule", "Food", "FoodEffect", "Tool", "ToolRule",
            "CustomModelData", "Equippable", "Jukebox", "UseCooldown"
    );
    private static final Set<String> COMPRESSED_FIELDS = Set.of("internal", "unhandled", "custom");

    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) {
        ItemStack captured = Objects.requireNonNull(item, "item").clone();
        requireItem(captured);
        int dataVersion = Bukkit.getUnsafe().getDataVersion();
        try {
            BoundedOutput output = new BoundedOutput();
            DataOutputStream data = new DataOutputStream(output);
            data.writeInt(MAGIC);
            data.writeInt(dataVersion);
            writeValue(data, itemValue(captured, dataVersion), new Budget(), 0);
            String encoded = PREFIX + Base64.getEncoder().encodeToString(output.bytes());
            ItemStack restored = decode(encoded);
            if (captured.getAmount() != restored.getAmount() || !captured.isSimilar(restored)) {
                throw new IllegalArgumentException("The server cannot restore this item's complete metadata");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to encode item data", exception);
        }
    }

    public static void validate(String encoded) {
        readEnvelope(encoded);
    }

    public static ItemStack decode(String encoded) {
        Envelope envelope = readEnvelope(encoded);
        if (envelope.dataVersion() > Bukkit.getUnsafe().getDataVersion()) {
            throw new IllegalArgumentException("Item data was captured on a newer Minecraft version");
        }
        Object restored = restore(envelope.item());
        if (!(restored instanceof ItemStack item)) {
            throw new IllegalArgumentException("Item data did not restore an ItemStack");
        }
        requireItem(item);
        return item;
    }

    private static void requireItem(ItemStack item) {
        if (item.getType().isAir() || item.getAmount() < 1 || item.getAmount() > 99) {
            throw new IllegalArgumentException("Captured items must contain between 1 and 99 non-air items");
        }
    }

    private static SerializedValue itemValue(ItemStack item, int dataVersion) {
        Map<String, Object> values = new LinkedHashMap<>(4);
        values.put("v", dataVersion);
        values.put("type", item.getType().name());
        values.put("amount", item.getAmount());
        if (item.hasItemMeta()) {
            values.put("meta", item.getItemMeta());
        }
        return new SerializedValue("ItemStack", values);
    }

    private static Envelope readEnvelope(String encoded) {
        if (encoded == null || encoded.length() > MAX_ENCODED_LENGTH || !encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid item data format or size");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
            if (bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Item data exceeds the size limit");
            }
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC) {
                throw new IllegalArgumentException("Invalid item data header");
            }
            int dataVersion = input.readInt();
            if (dataVersion < 1) {
                throw new IllegalArgumentException("Invalid item data version");
            }
            Object value = readValue(input, new Budget(), 0);
            if (!(value instanceof SerializedValue item) || !item.alias().equals("ItemStack") || input.available() != 0) {
                throw new IllegalArgumentException("Item data must contain exactly one ItemStack");
            }
            inspect(value, dataVersion, new Budget());
            return new Envelope(dataVersion, item);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Malformed item data", exception);
        }
    }

    private static void writeValue(DataOutputStream output, Object value, Budget budget, int depth) throws IOException {
        budget.visit(depth);
        if (value == null) {
            output.writeByte(0);
        } else if (value instanceof String text) {
            output.writeByte(1);
            writeString(output, text);
        } else if (value instanceof Boolean flag) {
            output.writeByte(2);
            output.writeBoolean(flag);
        } else if (value instanceof Byte number) {
            output.writeByte(3);
            output.writeByte(number);
        } else if (value instanceof Short number) {
            output.writeByte(4);
            output.writeShort(number);
        } else if (value instanceof Integer number) {
            output.writeByte(5);
            output.writeInt(number);
        } else if (value instanceof Long number) {
            output.writeByte(6);
            output.writeLong(number);
        } else if (value instanceof Float number && Float.isFinite(number)) {
            output.writeByte(7);
            output.writeFloat(number);
        } else if (value instanceof Double number && Double.isFinite(number)) {
            output.writeByte(8);
            output.writeDouble(number);
        } else if (value instanceof Map<?, ?> map) {
            output.writeByte(9);
            writeMap(output, map, budget, depth);
        } else if (value instanceof List<?> list) {
            output.writeByte(10);
            output.writeInt(collectionSize(list.size()));
            for (Object entry : list) {
                writeValue(output, entry, budget, depth + 1);
            }
        } else if (value instanceof byte[] array) {
            output.writeByte(11);
            output.writeInt(arraySize(array.length, 1));
            output.write(array);
        } else if (value instanceof int[] array) {
            output.writeByte(12);
            output.writeInt(arraySize(array.length, 4));
            for (int entry : array) {
                output.writeInt(entry);
            }
        } else if (value instanceof long[] array) {
            output.writeByte(13);
            output.writeInt(arraySize(array.length, 8));
            for (long entry : array) {
                output.writeLong(entry);
            }
        } else if (value instanceof SerializedValue serialized) {
            writeSerialized(output, serialized, budget, depth);
        } else if (value instanceof ItemStack item) {
            writeSerialized(output, itemValue(item, Bukkit.getUnsafe().getDataVersion()), budget, depth);
        } else if (value instanceof ConfigurationSerializable serializable) {
            String alias = ConfigurationSerialization.getAlias(serializable.getClass());
            requireTrustedType(alias);
            writeSerialized(output, new SerializedValue(alias, serializable.serialize()), budget, depth);
        } else {
            throw new IllegalArgumentException("Unsupported item data value: " + value.getClass().getName());
        }
    }

    private static void writeSerialized(DataOutputStream output, SerializedValue value, Budget budget, int depth) throws IOException {
        requireAlias(value.alias());
        output.writeByte(14);
        writeString(output, value.alias());
        writeMap(output, value.values(), budget, depth);
    }

    private static void writeMap(DataOutputStream output, Map<?, ?> map, Budget budget, int depth) throws IOException {
        output.writeInt(collectionSize(map.size()));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.equals("==")) {
                throw new IllegalArgumentException("Item data requires plain string map keys");
            }
            writeString(output, key);
            writeValue(output, entry.getValue(), budget, depth + 1);
        }
    }

    private static Object readValue(DataInputStream input, Budget budget, int depth) throws IOException {
        budget.visit(depth);
        return switch (input.readUnsignedByte()) {
            case 0 -> null;
            case 1 -> readString(input);
            case 2 -> readBoolean(input);
            case 3 -> input.readByte();
            case 4 -> input.readShort();
            case 5 -> input.readInt();
            case 6 -> input.readLong();
            case 7 -> finite(input.readFloat());
            case 8 -> finite(input.readDouble());
            case 9 -> readMap(input, budget, depth);
            case 10 -> readList(input, budget, depth);
            case 11 -> readBytes(input, arraySize(input.readInt(), 1));
            case 12 -> readInts(input);
            case 13 -> readLongs(input);
            case 14 -> readSerialized(input, budget, depth);
            default -> throw new IllegalArgumentException("Unknown item data value type");
        };
    }

    private static SerializedValue readSerialized(DataInputStream input, Budget budget, int depth) throws IOException {
        String alias = readString(input);
        requireAlias(alias);
        return new SerializedValue(alias, readMap(input, budget, depth));
    }

    private static Map<String, Object> readMap(DataInputStream input, Budget budget, int depth) throws IOException {
        int size = collectionSize(input.readInt());
        Map<String, Object> map = new LinkedHashMap<>(size);
        for (int index = 0; index < size; index++) {
            String key = readString(input);
            if (key.equals("==") || map.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate or reserved item data map key");
            }
            map.put(key, readValue(input, budget, depth + 1));
        }
        return map;
    }

    private static List<Object> readList(DataInputStream input, Budget budget, int depth) throws IOException {
        int size = collectionSize(input.readInt());
        List<Object> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            values.add(readValue(input, budget, depth + 1));
        }
        return values;
    }

    private static int[] readInts(DataInputStream input) throws IOException {
        int size = arraySize(input.readInt(), 4);
        requireRemaining(input, size * 4);
        int[] values = new int[size];
        for (int index = 0; index < size; index++) {
            values[index] = input.readInt();
        }
        return values;
    }

    private static long[] readLongs(DataInputStream input) throws IOException {
        int size = arraySize(input.readInt(), 8);
        requireRemaining(input, size * 8);
        long[] values = new long[size];
        for (int index = 0; index < size; index++) {
            values[index] = input.readLong();
        }
        return values;
    }

    private static boolean readBoolean(DataInputStream input) throws IOException {
        int value = input.readUnsignedByte();
        if (value > 1) {
            throw new IllegalArgumentException("Invalid item data boolean");
        }
        return value == 1;
    }

    private static float finite(float number) {
        if (!Float.isFinite(number)) {
            throw new IllegalArgumentException("Non-finite item data number");
        }
        return number;
    }

    private static double finite(double number) {
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Non-finite item data number");
        }
        return number;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Item data string exceeds the size limit");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(arraySize(bytes.length, 1));
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        byte[] bytes = readBytes(input, arraySize(input.readInt(), 1));
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Malformed UTF-8 in item data", exception);
        }
    }

    private static byte[] readBytes(DataInputStream input, int size) throws IOException {
        requireRemaining(input, size);
        return input.readNBytes(size);
    }

    private static void requireRemaining(DataInputStream input, int size) throws IOException {
        if (size > input.available()) {
            throw new IllegalArgumentException("Truncated item data");
        }
    }

    private static int collectionSize(int size) {
        if (size < 0 || size > MAX_COLLECTION) {
            throw new IllegalArgumentException("Item data collection exceeds the size limit");
        }
        return size;
    }

    private static int arraySize(int size, int width) {
        if (size < 0 || size > MAX_BYTES / width) {
            throw new IllegalArgumentException("Item data array exceeds the size limit");
        }
        return size;
    }

    private static void requireAlias(String alias) {
        if (!ALIASES.contains(alias)) {
            throw new IllegalArgumentException("Unsupported item data alias: " + alias);
        }
    }

    private static Class<? extends ConfigurationSerializable> requireTrustedType(String alias) {
        requireAlias(alias);
        if (alias.equals("ItemStack")) {
            return ItemStack.class;
        }
        Class<? extends ConfigurationSerializable> type = ConfigurationSerialization.getClassByAlias(alias);
        if (type == null || !type.getName().startsWith("org.bukkit.")
                || (type.getClassLoader() != ItemStack.class.getClassLoader()
                && type.getClassLoader() != Bukkit.getServer().getClass().getClassLoader())) {
            throw new IllegalArgumentException("Item data type is unavailable or is not supplied by this server: " + alias);
        }
        return type;
    }

    private static Object restore(Object value) {
        if (value instanceof SerializedValue serialized) {
            Class<? extends ConfigurationSerializable> type = requireTrustedType(serialized.alias());
            ConfigurationSerializable restored = ConfigurationSerialization.deserializeObject(restoreMap(serialized.values()), type);
            if (restored == null) {
                throw new IllegalArgumentException("The server could not restore item data: " + serialized.alias());
            }
            return restored;
        }
        if (value instanceof Map<?, ?> map) {
            return restoreMap(map);
        }
        if (value instanceof List<?> values) {
            List<Object> restored = new ArrayList<>(values.size());
            for (Object entry : values) {
                restored.add(restore(entry));
            }
            return restored;
        }
        return value;
    }

    private static Map<String, Object> restoreMap(Map<?, ?> values) {
        Map<String, Object> restored = new LinkedHashMap<>(values.size());
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            restored.put((String) entry.getKey(), restore(entry.getValue()));
        }
        return restored;
    }

    private static void inspect(Object value, int dataVersion, Budget budget) throws IOException {
        if (value instanceof SerializedValue serialized) {
            if (serialized.alias().equals("ItemStack")) {
                Object version = serialized.values().get("v");
                if (!(version instanceof Integer number) || number < 1 || number > dataVersion) {
                    throw new IllegalArgumentException("Invalid nested item data version");
                }
            }
            if (serialized.alias().equals("ItemMeta")) {
                for (String field : COMPRESSED_FIELDS) {
                    if (serialized.values().get(field) instanceof String encoded) {
                        inspectCompressed(encoded, budget);
                    }
                }
            }
            inspect(serialized.values(), dataVersion, budget);
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                inspect(entry.getValue(), dataVersion, budget);
            }
        } else if (value instanceof List<?> values) {
            for (Object entry : values) {
                inspect(entry, dataVersion, budget);
            }
        }
    }

    private static void inspectCompressed(String encoded, Budget budget) throws IOException {
        byte[] compressed = Base64.getDecoder().decode(encoded);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] bytes = gzip.readNBytes(MAX_BYTES - budget.expandedBytes + 1);
            budget.expandedBytes += bytes.length;
            if (budget.expandedBytes > MAX_BYTES) {
                throw new IllegalArgumentException("Compressed item metadata exceeds the size limit");
            }
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            int type = input.readUnsignedByte();
            input.readUTF();
            inspectNbt(input, type, budget, 0);
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing data in compressed item metadata");
            }
        }
    }

    private static void inspectNbt(DataInputStream input, int type, Budget budget, int depth) throws IOException {
        budget.visit(depth);
        switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3, 5 -> input.readInt();
            case 4, 6 -> input.readLong();
            case 7, 11, 12 -> {
                int width = type == 7 ? 1 : type == 11 ? 4 : 8;
                int size = arraySize(input.readInt(), width) * width;
                requireRemaining(input, size);
                input.skipNBytes(size);
            }
            case 8 -> input.readUTF();
            case 9 -> {
                int childType = input.readUnsignedByte();
                int size = collectionSize(input.readInt());
                for (int index = 0; index < size; index++) {
                    inspectNbt(input, childType, budget, depth + 1);
                }
            }
            case 10 -> {
                int childType;
                while ((childType = input.readUnsignedByte()) != 0) {
                    input.readUTF();
                    inspectNbt(input, childType, budget, depth + 1);
                }
            }
            default -> throw new IllegalArgumentException("Invalid compressed item metadata tag");
        }
    }

    private record SerializedValue(String alias, Map<String, Object> values) {
    }

    private record Envelope(int dataVersion, SerializedValue item) {
    }

    private static final class Budget {
        private int nodes;
        private int expandedBytes;

        private void visit(int depth) {
            if (depth > MAX_DEPTH || ++nodes > MAX_NODES) {
                throw new IllegalArgumentException("Item data exceeds the nesting or node limit");
            }
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        @Override
        public void write(int value) {
            if (output.size() == MAX_BYTES) {
                throw new IllegalArgumentException("Item data exceeds the size limit");
            }
            output.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            if (length > MAX_BYTES - output.size()) {
                throw new IllegalArgumentException("Item data exceeds the size limit");
            }
            output.write(bytes, offset, length);
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
