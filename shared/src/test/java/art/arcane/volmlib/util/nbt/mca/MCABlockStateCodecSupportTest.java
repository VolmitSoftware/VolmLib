package art.arcane.volmlib.util.nbt.mca;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MCABlockStateCodecSupportTest {
    @Test
    public void encodesCapitalizedBlockStateForEarlierServers() {
        CompoundTag tag = MCABlockStateCodecSupport.encodeBlockState(
                "minecraft:oak_log[axis=x]", "minecraft:oak_log", MCABlockStateCodecSupport.Format.CAPITALIZED);

        assertEquals("minecraft:oak_log", tag.getString("Name"));
        assertEquals("x", tag.getCompoundTag("Properties").getString("axis"));
        assertFalse(tag.containsKey("id"));
        assertEquals("minecraft:oak_log[axis=x]", MCABlockStateCodecSupport.decodeBlockStateString(
                tag, MCABlockStateCodecSupport.Format.CAPITALIZED));
    }

    @Test
    public void readsAndWritesLowercaseBlockStateFor26_3() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:oak_log");
        CompoundTag properties = new CompoundTag();
        properties.putString("axis", "z");
        tag.put("properties", properties);

        assertEquals("minecraft:oak_log[axis=z]", MCABlockStateCodecSupport.decodeBlockStateString(
                tag, MCABlockStateCodecSupport.Format.LOWERCASE));
        assertEquals(tag, MCABlockStateCodecSupport.encodeBlockState(
                "minecraft:oak_log[axis=z]", "minecraft:oak_log", MCABlockStateCodecSupport.Format.LOWERCASE));
        assertFalse(tag.containsKey("Name"));
    }

    @Test
    public void readsScalarAndMixedPaletteBlockStates() {
        NBTWorldSupport.BlockStateCodec<String> lowercase = codec(MCABlockStateCodecSupport.Format.LOWERCASE);
        CompoundTag wrapped = new CompoundTag();
        wrapped.putString("", "minecraft:stone");

        assertEquals("minecraft:stone", lowercase.decode(new StringTag("minecraft:stone")));
        assertEquals("minecraft:stone", lowercase.decode(wrapped));
    }

    @Test
    public void defaultBlockStatesNeedNoProperties() {
        for (MCABlockStateCodecSupport.Format format : MCABlockStateCodecSupport.Format.values()) {
            CompoundTag tag = MCABlockStateCodecSupport.encodeBlockState("minecraft:stone", "minecraft:stone", format);
            assertFalse(tag.containsKey(format.propertiesKey()));
            assertEquals("minecraft:stone", MCABlockStateCodecSupport.decodeBlockStateString(tag, format));
            assertNull(MCABlockStateCodecSupport.decodeBlockStateString(null, format));
        }
    }

    @Test
    public void cachedCodecsKeepTheirSelectedFormat() {
        NBTWorldSupport.BlockStateCodec<String> capitalized = codec(MCABlockStateCodecSupport.Format.CAPITALIZED);
        NBTWorldSupport.BlockStateCodec<String> lowercase = codec(MCABlockStateCodecSupport.Format.LOWERCASE);

        assertEquals("minecraft:stone", capitalized.encode("minecraft:stone").getString("Name"));
        assertEquals("minecraft:stone", lowercase.encode("minecraft:stone").getString("id"));
        assertSame(lowercase.encode("minecraft:stone"), lowercase.encode("minecraft:stone"));
        assertEquals("minecraft:stone", lowercase.decode(lowercase.encode("minecraft:stone")));
        assertEquals("minecraft:air", lowercase.decode(null));
    }

    private NBTWorldSupport.BlockStateCodec<String> codec(MCABlockStateCodecSupport.Format format) {
        return NBTWorldSupport.blockStateCodec(new NBTWorldSupport.BlockStateCodecOptions<>(
                Function.identity(), () -> "minecraft:air", Function.identity(), Function.identity(), format));
    }
}
