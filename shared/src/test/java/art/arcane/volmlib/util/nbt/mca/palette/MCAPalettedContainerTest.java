package art.arcane.volmlib.util.nbt.mca.palette;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import art.arcane.volmlib.util.nbt.tag.StringTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class MCAPalettedContainerTest {
    @Test
    public void readsStringEntriesThroughLinearAndHashPalettes() {
        assertStringPalette(3);
        assertStringPalette(17);
    }

    @Test
    public void passesMixedListWrappersToTheConfiguredDecoder() {
        CompoundTag stone = new CompoundTag();
        stone.putString("", "minecraft:stone");
        CompoundTag stairs = new CompoundTag();
        stairs.putString("id", "minecraft:oak_stairs");
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "east");
        stairs.put("properties", properties);
        ListTag<CompoundTag> palette = new ListTag<>(CompoundTag.class);
        palette.add(stone);
        palette.add(stairs);
        MCAIdMapper<Tag<?>> registry = new MCAIdMapper<>();
        registry.add(stone);
        registry.add(stairs);
        MCAPalettedContainer<Tag<?>> container = new MCAPalettedContainer<>(
                new MCAGlobalPalette<>(registry, stone), registry, tag -> tag,
                tag -> (CompoundTag) tag, stone);
        MCABitStorage storage = new MCABitStorage(4, 4096);
        storage.set(1, 1);

        container.read(palette, storage.getRaw());

        assertSame(stone, container.get(0, 0, 0));
        assertSame(stairs, container.get(1, 0, 0));
    }

    @Test
    public void preservesCompoundPaletteWriteAndRead() {
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        CompoundTag stone = new CompoundTag();
        stone.putString("Name", "minecraft:stone");
        MCAIdMapper<CompoundTag> registry = new MCAIdMapper<>();
        registry.add(air);
        registry.add(stone);
        MCAPalettedContainer<CompoundTag> source = new MCAPalettedContainer<>(
                new MCAGlobalPalette<>(registry, air), registry,
                tag -> (CompoundTag) tag, tag -> tag, air);
        source.set(5, 7, 9, stone);
        CompoundTag section = new CompoundTag();

        source.write(section, "Palette", "BlockStates");

        MCAPalettedContainer<CompoundTag> restored = new MCAPalettedContainer<>(
                new MCAGlobalPalette<>(registry, air), registry,
                tag -> (CompoundTag) tag, tag -> tag, air);
        restored.read(section.getListTag("Palette"), section.getLongArrayTag("BlockStates").getValue());
        assertEquals(CompoundTag.class, section.getListTag("Palette").getTypeClass());
        assertEquals("minecraft:stone", restored.get(5, 7, 9).getString("Name"));
        assertEquals("minecraft:air", restored.get(0, 0, 0).getString("Name"));
    }

    private static void assertStringPalette(int paletteSize) {
        ListTag<StringTag> palette = new ListTag<>(StringTag.class);
        MCAIdMapper<Tag<?>> registry = new MCAIdMapper<>();
        for (int index = 0; index < paletteSize; index++) {
            StringTag entry = new StringTag("minecraft:block_" + index);
            palette.add(entry);
            registry.add(entry);
        }
        Tag<?> defaultValue = palette.get(0);
        MCAPalettedContainer<Tag<?>> container = new MCAPalettedContainer<>(
                new MCAGlobalPalette<>(registry, defaultValue), registry, tag -> tag,
                tag -> {
                    CompoundTag compound = new CompoundTag();
                    compound.putString("id", ((StringTag) tag).getValue());
                    return compound;
                }, defaultValue);
        MCABitStorage storage = new MCABitStorage(Math.max(4, MCAMth.ceillog2(paletteSize)), 4096);
        for (int index = 0; index < paletteSize; index++) {
            storage.set(index, index);
        }

        container.read(palette, storage.getRaw());

        for (int index = 0; index < paletteSize; index++) {
            assertSame(palette.get(index), container.get(index & 15, 0, index >> 4));
        }
        assertSame(defaultValue, container.get(15, 15, 15));
    }
}
