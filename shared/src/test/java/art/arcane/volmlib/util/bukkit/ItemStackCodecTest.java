package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ItemStackCodecTest {
    private static final int VERSION = 3465;
    private static final String FIXTURE = "vitem1:VklUTQAADYkOAAAACUl0ZW1TdGFjawAAAAMAAAABdgUAAA2JAAAABHR5cGUBAAAAB0RJQU1PTkQAAAAGYW1vdW50BQAAAAI=";

    @Test
    public void validatesWithoutAccessingBukkit() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            ItemStackCodec.validate(FIXTURE);
            bukkit.verifyNoInteractions();
        }
    }

    @Test
    public void decodesPortableMaterialAndAmountThroughBaselineBukkit() {
        UnsafeValues unsafe = mock(UnsafeValues.class);
        ItemFactory factory = mock(ItemFactory.class);
        Server server = mock(Server.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION);
        when(unsafe.getMaterial("DIAMOND", VERSION)).thenReturn(Material.DIAMOND);
        when(factory.equals(null, null)).thenReturn(true);
        when(factory.ensureServerConversions(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(server.getItemFactory()).thenReturn(factory);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            bukkit.when(Bukkit::getItemFactory).thenReturn(factory);
            bukkit.when(Bukkit::getServer).thenReturn(server);
            ItemStack item = ItemStackCodec.decode(FIXTURE);
            assertEquals(Material.DIAMOND, item.getType());
            assertEquals(2, item.getAmount());
            assertEquals(FIXTURE, ItemStackCodec.encode(item));
        }
    }

    @Test
    public void rejectsDowngradeBeforeServerRestoration() {
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION - 1);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ConfigurationSerialization> serialization = mockStatic(ConfigurationSerialization.class)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.decode(FIXTURE))
                    .getMessage().contains("newer Minecraft"));
            serialization.verifyNoInteractions();
        }
    }

    @Test
    public void rejectsMalformedEnvelopeLengthsHeadersAndTrailingBytes() throws IOException {
        for (String invalid : List.of("", "other:" + FIXTURE, "vitem1:%%%", "vitem1:AA==", "x".repeat(ItemStackCodec.MAX_ENCODED_LENGTH + 1))) {
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(null));
        byte[] bytes = Base64.getDecoder().decode(FIXTURE.substring(7));
        bytes[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(wrap(bytes)));
        ByteArrayOutputStream trailing = new ByteArrayOutputStream();
        trailing.write(Base64.getDecoder().decode(FIXTURE.substring(7)));
        trailing.write(0);
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(wrap(trailing.toByteArray())));
    }

    @Test
    public void rejectsUnknownAndJavaSerializationAliases() throws IOException {
        for (String alias : List.of("java.lang.Runtime", "ExamplePluginPayload", "org.bukkit.Server")) {
            String encoded = fixture(new Serialized(alias, Map.of("v", VERSION)));
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
        }
        String nested = fixture(item(Map.of("meta", new Serialized("ExamplePluginPayload", Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(nested));
    }

    @Test
    public void rejectsPluginClassRegisteredUnderBuiltinAlias() throws IOException {
        String encoded = fixture(item(Map.of("meta", new Serialized("ItemMeta", Map.of()))));
        UnsafeValues unsafe = mock(UnsafeValues.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ConfigurationSerialization> serialization = mockStatic(ConfigurationSerialization.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            serialization.when(() -> ConfigurationSerialization.getClassByAlias("ItemMeta")).thenReturn(ForeignValue.class);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.decode(encoded))
                    .getMessage().contains("not supplied by this server"));
        }
    }

    @Test
    public void rejectsInvalidNestedDataVersions() throws IOException {
        for (Object invalid : List.of(-1, 0, VERSION + 1, "3465", 3465L)) {
            String encoded = fixture(new Serialized("ItemStack", Map.of("v", invalid, "type", "DIAMOND")));
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
        }
        String missing = fixture(new Serialized("ItemStack", Map.of("type", "DIAMOND")));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(missing));
        String child = fixture(item(Map.of("nested", new Serialized("ItemStack", Map.of("v", VERSION + 1)))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(child));
    }

    @Test
    public void rejectsReservedMapKeysAndNonFiniteNumbers() throws IOException {
        String reserved = fixture(item(Map.of("meta", Map.of("==", "java.lang.Runtime"))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(reserved));
        for (Object number : List.of(Float.NaN, Float.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            String encoded = fixture(item(Map.of("value", number)));
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
        }
    }

    @Test
    public void rejectsExcessiveDepthAndNodeCounts() throws IOException {
        Object nested = "leaf";
        for (int index = 0; index < 40; index++) {
            nested = List.of(nested);
        }
        String deep = fixture(item(Map.of("nested", nested)));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(deep));
        List<Object> group = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            group.add(null);
        }
        String nodes = fixture(item(Map.of("groups", List.of(group, group, group))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(nodes));
    }

    @Test
    public void rejectsOversizedAndTruncatedArraysBeforeAllocation() throws IOException {
        for (int type : List.of(11, 12, 13)) {
            for (int size : List.of(-1, Integer.MAX_VALUE, 1000)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeInt(0x5649544D);
                output.writeInt(VERSION);
                output.writeByte(type);
                output.writeInt(size);
                assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(wrap(bytes.toByteArray())));
            }
        }
    }

    @Test
    public void preservesPrimitiveTypesAndNestedMetadataBeforeBukkitRestoration() throws IOException {
        Map<String, Object> pdc = new LinkedHashMap<>();
        pdc.put("bytes", new byte[]{1, -2, 3});
        pdc.put("ints", new int[]{Integer.MIN_VALUE, 44});
        pdc.put("longs", new long[]{Long.MAX_VALUE});
        pdc.put("small", (byte) 2);
        pdc.put("short", (short) 12);
        pdc.put("flag", true);
        pdc.put("float", 1.25F);
        pdc.put("double", 2.75D);
        pdc.put("null", null);
        Map<String, Object> meta = Map.of("display-name", "A §6name", "lore", List.of("Line one", "Ω line two"),
                "enchants", Map.of("sharpness", 7), "custom-model-data", 504, "PublicBukkitValues", pdc,
                "color", new Serialized("Color", Map.of("RED", 4, "GREEN", 5, "BLUE", 6)));
        String encoded = fixture(item(Map.of("meta", new Serialized("ItemMeta", meta))));
        AtomicReference<Map<String, Object>> restoredMeta = new AtomicReference<>();
        UnsafeValues unsafe = mock(UnsafeValues.class);
        ItemStack restored = mock(ItemStack.class);
        ItemMeta restoredItemMeta = mock(ItemMeta.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION);
        when(restored.getType()).thenReturn(Material.DIAMOND);
        when(restored.getAmount()).thenReturn(2);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ConfigurationSerialization> serialization = mockStatic(ConfigurationSerialization.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            serialization.when(() -> ConfigurationSerialization.getClassByAlias("ItemMeta")).thenReturn(ItemMeta.class);
            serialization.when(() -> ConfigurationSerialization.deserializeObject(any(), eq(ItemMeta.class))).thenAnswer(invocation -> {
                restoredMeta.set(invocation.getArgument(0));
                return restoredItemMeta;
            });
            serialization.when(() -> ConfigurationSerialization.deserializeObject(any(), eq(ItemStack.class))).thenReturn(restored);
            ItemStackCodec.decode(encoded);
        }
        Map<?, ?> result = (Map<?, ?>) restoredMeta.get().get("PublicBukkitValues");
        assertArrayEquals((byte[]) pdc.get("bytes"), (byte[]) result.get("bytes"));
        assertArrayEquals((int[]) pdc.get("ints"), (int[]) result.get("ints"));
        assertArrayEquals((long[]) pdc.get("longs"), (long[]) result.get("longs"));
        for (String key : List.of("small", "short", "flag", "float", "double")) {
            assertEquals(pdc.get(key), result.get(key));
            assertEquals(pdc.get(key).getClass(), result.get(key).getClass());
        }
        assertTrue(result.containsKey("null"));
        assertEquals(meta.get("lore"), restoredMeta.get().get("lore"));
        assertEquals(meta.get("enchants"), restoredMeta.get().get("enchants"));
        assertEquals(504, restoredMeta.get().get("custom-model-data"));
        assertEquals(Color.fromRGB(4, 5, 6), restoredMeta.get().get("color"));
    }

    @Test
    public void rejectsLossyCaptureAndInvalidHeldAmounts() {
        UnsafeValues unsafe = mock(UnsafeValues.class);
        ItemStack original = mock(ItemStack.class);
        ItemStack restored = mock(ItemStack.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION);
        when(original.clone()).thenReturn(original);
        when(original.getType()).thenReturn(Material.DIAMOND);
        when(original.getAmount()).thenReturn(2);
        when(restored.getType()).thenReturn(Material.DIAMOND);
        when(restored.getAmount()).thenReturn(2);
        when(original.isSimilar(restored)).thenReturn(false);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ConfigurationSerialization> serialization = mockStatic(ConfigurationSerialization.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            serialization.when(() -> ConfigurationSerialization.deserializeObject(any(), eq(ItemStack.class))).thenReturn(restored);
            assertTrue(assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.encode(original))
                    .getMessage().contains("complete metadata"));
            when(original.getAmount()).thenReturn(100);
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.encode(original));
            when(original.getAmount()).thenReturn(0);
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.encode(original));
            when(original.getAmount()).thenReturn(1);
            when(original.getType()).thenReturn(Material.AIR);
            assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.encode(original));
        }
    }

    @Test
    public void encodesPortableMetadataWithoutDependingOnPlatformItemSchema() {
        UnsafeValues unsafe = mock(UnsafeValues.class);
        ItemStack captured = mock(ItemStack.class);
        ItemStack restored = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(unsafe.getDataVersion()).thenReturn(VERSION);
        when(captured.clone()).thenReturn(captured);
        when(captured.getType()).thenReturn(Material.DIAMOND);
        when(captured.getAmount()).thenReturn(2);
        when(captured.hasItemMeta()).thenReturn(true);
        when(captured.getItemMeta()).thenReturn(meta);
        when(meta.serialize()).thenReturn(Map.of("display-name", "A custom item", "custom-model-data", 908,
                "PublicBukkitValues", Map.of("example:serial", new long[]{17L, 19L})));
        when(restored.getType()).thenReturn(Material.DIAMOND);
        when(restored.getAmount()).thenReturn(2);
        when(captured.isSimilar(restored)).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<ConfigurationSerialization> serialization = mockStatic(ConfigurationSerialization.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            serialization.when(() -> ConfigurationSerialization.getAlias(meta.getClass())).thenReturn("ItemMeta");
            serialization.when(() -> ConfigurationSerialization.getClassByAlias("ItemMeta")).thenReturn(ItemMeta.class);
            serialization.when(() -> ConfigurationSerialization.deserializeObject(any(), eq(ItemMeta.class))).thenReturn(meta);
            serialization.when(() -> ConfigurationSerialization.deserializeObject(any(), eq(ItemStack.class))).thenReturn(restored);
            String encoded = ItemStackCodec.encode(captured);
            ItemStackCodec.validate(encoded);
            assertTrue(encoded.length() < ItemStackCodec.MAX_ENCODED_LENGTH);
            verify(captured, never()).serialize();
            verify(captured).isSimilar(restored);
            verify(meta).serialize();
        }
    }

    @Test
    public void leavesMatchingPdcFieldNamesAsOrdinaryData() throws IOException {
        ItemStackCodec.validate(fixture(itemMeta(Map.of("PublicBukkitValues",
                Map.of("example:data", Map.of("internal", "plain text", "custom", "also text", "unhandled", "text"))))));
    }

    @Test
    public void boundsCumulativeDecompressionAcrossMetadataFields() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(7);
        output.writeUTF("");
        output.writeInt(50000);
        output.write(new byte[50000]);
        String compressed = gzip(bytes.toByteArray());
        ItemStackCodec.validate(fixture(itemMeta(Map.of("internal", compressed))));
        String encoded = fixture(itemMeta(Map.of("internal", compressed, "custom", compressed)));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
    }

    @Test
    public void rejectsDeepCompressedNbtBeforeBukkit() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        for (int index = 0; index < 40; index++) {
            output.writeByte(10);
            output.writeUTF("");
        }
        output.write(new byte[40]);
        String encoded = fixture(itemMeta(Map.of("internal", gzip(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
    }

    @Test
    public void boundsCompressedMetadataAndAcceptsSmallCompound() throws IOException {
        byte[] compound = {10, 0, 0, 3, 0, 1, 'x', 0, 0, 0, 8, 0};
        ItemStackCodec.validate(fixture(itemMeta(Map.of("internal", gzip(compound)))));
        String bomb = fixture(itemMeta(Map.of("internal", gzip(new byte[100000]))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(bomb));
        String invalid = fixture(itemMeta(Map.of("unhandled", gzip(new byte[]{100, 0, 0}))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(invalid));
        String badBase64 = fixture(itemMeta(Map.of("custom", "%%%")));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(badBase64));
    }

    @Test
    public void rejectsOversizedArrayDeclarationsInsideCompressedMetadata() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeByte(7);
        output.writeUTF("");
        output.writeInt(Integer.MAX_VALUE);
        String encoded = fixture(itemMeta(Map.of("internal", gzip(bytes.toByteArray()))));
        assertThrows(IllegalArgumentException.class, () -> ItemStackCodec.validate(encoded));
    }

    private static Serialized item(Map<String, Object> additional) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("v", VERSION);
        values.put("type", "DIAMOND");
        values.put("amount", 2);
        values.putAll(additional);
        return new Serialized("ItemStack", values);
    }

    private static Serialized itemMeta(Map<String, Object> values) {
        return item(Map.of("meta", new Serialized("ItemMeta", values)));
    }

    private static String fixture(Object root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x5649544D);
        output.writeInt(VERSION);
        writeValue(output, root);
        return wrap(bytes.toByteArray());
    }

    private static String wrap(byte[] bytes) {
        return "vitem1:" + Base64.getEncoder().encodeToString(bytes);
    }

    private static String gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static void writeValue(DataOutputStream output, Object value) throws IOException {
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
        } else if (value instanceof Float number) {
            output.writeByte(7);
            output.writeFloat(number);
        } else if (value instanceof Double number) {
            output.writeByte(8);
            output.writeDouble(number);
        } else if (value instanceof Map<?, ?> map) {
            output.writeByte(9);
            writeMap(output, map);
        } else if (value instanceof List<?> list) {
            output.writeByte(10);
            output.writeInt(list.size());
            for (Object entry : list) {
                writeValue(output, entry);
            }
        } else if (value instanceof byte[] array) {
            output.writeByte(11);
            output.writeInt(array.length);
            output.write(array);
        } else if (value instanceof int[] array) {
            output.writeByte(12);
            output.writeInt(array.length);
            for (int entry : array) {
                output.writeInt(entry);
            }
        } else if (value instanceof long[] array) {
            output.writeByte(13);
            output.writeInt(array.length);
            for (long entry : array) {
                output.writeLong(entry);
            }
        } else if (value instanceof Serialized serialized) {
            output.writeByte(14);
            writeString(output, serialized.alias());
            writeMap(output, serialized.values());
        } else {
            throw new IllegalArgumentException("Unsupported test value");
        }
    }

    private static void writeMap(DataOutputStream output, Map<?, ?> map) throws IOException {
        output.writeInt(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writeString(output, (String) entry.getKey());
            writeValue(output, entry.getValue());
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private record Serialized(String alias, Map<String, Object> values) {
    }

    private static final class ForeignValue implements ConfigurationSerializable {
        @Override
        public Map<String, Object> serialize() {
            return Map.of();
        }
    }
}
