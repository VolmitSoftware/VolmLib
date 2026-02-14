/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.nbt.mca;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.nbt.io.NBTDeserializer;
import art.arcane.volmlib.util.nbt.io.NBTSerializer;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.mca.palette.MCABiomeContainer;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.nbt.mca.LoadFlags.ALL_DATA;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.BIOMES;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.BLOCK_LIGHTS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.BLOCK_STATES;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.CARVING_MASKS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.ENTITIES;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.HEIGHTMAPS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.LIGHTS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.LIQUIDS_TO_BE_TICKED;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.LIQUID_TICKS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.POST_PROCESSING;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.SKY_LIGHT;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.STRUCTURES;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.TILE_ENTITIES;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.TILE_TICKS;
import static art.arcane.volmlib.util.nbt.mca.LoadFlags.TO_BE_TICKED;

public class MCAChunkSupport<S extends MCASectionLike> implements MCAChunkLike {
    public interface BiomeFactory {
        MCABiomeContainer create(int minHeight, int maxHeight);
    }

    public interface BiomeFactoryWithData {
        MCABiomeContainer create(int minHeight, int maxHeight, int[] data);
    }

    public interface SectionFactory<S extends MCASectionLike> {
        S create(CompoundTag sectionRoot, int dataVersion, long loadFlags);
    }

    public static class Config<S extends MCASectionLike> {
        public final int defaultDataVersion;
        public final IntSupplier minHeightSupplier;
        public final IntSupplier maxHeightSupplier;
        public final BiomeFactory biomeFactory;
        public final BiomeFactoryWithData biomeFactoryWithData;
        public final SectionFactory<S> sectionFactory;
        public final Supplier<S> newSectionFactory;
        public final Supplier<String> headlessGeneratorSupplier;
        public final Supplier<String> nativeGeneratorSupplier;

        public Config(
                int defaultDataVersion,
                IntSupplier minHeightSupplier,
                IntSupplier maxHeightSupplier,
                BiomeFactory biomeFactory,
                BiomeFactoryWithData biomeFactoryWithData,
                SectionFactory<S> sectionFactory,
                Supplier<S> newSectionFactory,
                Supplier<String> headlessGeneratorSupplier,
                Supplier<String> nativeGeneratorSupplier
        ) {
            this.defaultDataVersion = defaultDataVersion;
            this.minHeightSupplier = minHeightSupplier;
            this.maxHeightSupplier = maxHeightSupplier;
            this.biomeFactory = biomeFactory;
            this.biomeFactoryWithData = biomeFactoryWithData;
            this.sectionFactory = sectionFactory;
            this.newSectionFactory = newSectionFactory;
            this.headlessGeneratorSupplier = headlessGeneratorSupplier;
            this.nativeGeneratorSupplier = nativeGeneratorSupplier;
        }
    }

    private final KMap<Integer, S> sections = new KMap<>();
    private final Config<S> config;
    private boolean partial;
    private int lastMCAUpdate;
    private CompoundTag data;
    private int dataVersion;
    private int nativeIrisVersion;
    private long lastUpdate;
    private long inhabitedTime;
    private MCABiomeContainer biomes;
    private CompoundTag heightMaps;
    private CompoundTag carvingMasks;
    private ListTag<CompoundTag> entities;
    private ListTag<CompoundTag> tileEntities;
    private ListTag<CompoundTag> tileTicks;
    private ListTag<CompoundTag> liquidTicks;
    private ListTag<ListTag<?>> lights;
    private ListTag<ListTag<?>> liquidsToBeTicked;
    private ListTag<ListTag<?>> toBeTicked;
    private ListTag<ListTag<?>> postProcessing;
    private String status;
    private CompoundTag structures;

    protected MCAChunkSupport(int lastMCAUpdate, Config<S> config) {
        this.config = config;
        this.lastMCAUpdate = lastMCAUpdate;
    }

    protected MCAChunkSupport(CompoundTag data, Config<S> config) {
        this.config = config;
        this.data = data;
        initReferences(ALL_DATA);
        setStatus("full");
    }

    public void initializeNewChunk() {
        dataVersion = config.defaultDataVersion;
        data = new CompoundTag();
        biomes = config.biomeFactory.create(config.minHeightSupplier.getAsInt(), config.maxHeightSupplier.getAsInt());
        data.put("Level", defaultLevel());
        status = "full";
    }

    public void injectNativeData(String key) {
        data.put(key, nativeGeneratorTag());
    }

    private CompoundTag defaultLevel() {
        CompoundTag level = new CompoundTag();
        level.putString("Status", "full");
        level.putString("Generator", config.headlessGeneratorSupplier.get());
        return level;
    }

    private CompoundTag nativeGeneratorTag() {
        CompoundTag level = new CompoundTag();
        level.putString("Generator", config.nativeGeneratorSupplier.get());
        return level;
    }

    private void initReferences(long loadFlags) {
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }

        CompoundTag level = data;
        int minHeight = config.minHeightSupplier.getAsInt();
        int maxHeight = config.maxHeightSupplier.getAsInt();

        dataVersion = data.getInt("DataVersion");
        inhabitedTime = level.getLong("InhabitedTime");
        lastUpdate = level.getLong("LastUpdate");
        if ((loadFlags & BIOMES) != 0) {
            biomes = config.biomeFactoryWithData.create(minHeight, maxHeight, level.getIntArray("Biomes"));
        }
        if ((loadFlags & HEIGHTMAPS) != 0) {
            heightMaps = level.getCompoundTag("Heightmaps");
        }
        if ((loadFlags & CARVING_MASKS) != 0) {
            carvingMasks = level.getCompoundTag("CarvingMasks");
        }
        if ((loadFlags & ENTITIES) != 0) {
            entities = level.containsKey("Entities") ? level.getListTag("Entities").asCompoundTagList() : null;
        }
        if ((loadFlags & TILE_ENTITIES) != 0) {
            tileEntities = level.containsKey("TileEntities") ? level.getListTag("TileEntities").asCompoundTagList() : null;
        }
        if ((loadFlags & TILE_TICKS) != 0) {
            tileTicks = level.containsKey("TileTicks") ? level.getListTag("TileTicks").asCompoundTagList() : null;
        }
        if ((loadFlags & LIQUID_TICKS) != 0) {
            liquidTicks = level.containsKey("LiquidTicks") ? level.getListTag("LiquidTicks").asCompoundTagList() : null;
        }
        if ((loadFlags & LIGHTS) != 0) {
            lights = level.containsKey("Lights") ? level.getListTag("Lights").asListTagList() : null;
        }
        if ((loadFlags & LIQUIDS_TO_BE_TICKED) != 0) {
            liquidsToBeTicked = level.containsKey("LiquidsToBeTicked") ? level.getListTag("LiquidsToBeTicked").asListTagList() : null;
        }
        if ((loadFlags & TO_BE_TICKED) != 0) {
            toBeTicked = level.containsKey("ToBeTicked") ? level.getListTag("ToBeTicked").asListTagList() : null;
        }
        if ((loadFlags & POST_PROCESSING) != 0) {
            postProcessing = level.containsKey("PostProcessing") ? level.getListTag("PostProcessing").asListTagList() : null;
        }
        status = level.getString("Status");
        if ((loadFlags & STRUCTURES) != 0) {
            structures = level.getCompoundTag("Structures");
        }

        if ((loadFlags & (BLOCK_LIGHTS | BLOCK_STATES | SKY_LIGHT)) != 0 && level.containsKey("Sections")) {
            for (CompoundTag section : level.getListTag("Sections").asCompoundTagList()) {
                int sectionIndex = section.getByte("Y");
                if (sectionIndex > 15 || sectionIndex < 0) {
                    continue;
                }
                S newSection = config.sectionFactory.create(section, dataVersion, loadFlags);
                if (newSection.isEmpty()) {
                    continue;
                }
                sections.put(sectionIndex, newSection);
            }
        }

        if (loadFlags != ALL_DATA) {
            data = null;
            partial = true;
        } else {
            partial = false;
        }
    }

    public int serialize(RandomAccessFile raf, int xPos, int zPos) throws IOException {
        if (partial) {
            throw new UnsupportedOperationException("Partially loaded chunks cannot be serialized");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        try (BufferedOutputStream nbtOut = new BufferedOutputStream(CompressionType.ZLIB.compress(baos))) {
            new NBTSerializer(false).toStream(new NamedTag(null, updateHandle(xPos, zPos)), nbtOut);
        }

        byte[] rawData = baos.toByteArray();
        raf.writeInt(rawData.length + 1);
        raf.writeByte(CompressionType.ZLIB.getID());
        raf.write(rawData);
        return rawData.length + 5;
    }

    public void deserialize(RandomAccessFile raf) throws IOException {
        deserialize(raf, ALL_DATA);
    }

    public void deserialize(RandomAccessFile raf, long loadFlags) throws IOException {
        byte compressionTypeByte = raf.readByte();
        CompressionType compressionType = CompressionType.getFromID(compressionTypeByte);
        if (compressionType == null) {
            throw new IOException("invalid compression type " + compressionTypeByte);
        }
        BufferedInputStream dis = new BufferedInputStream(compressionType.decompress(new FileInputStream(raf.getFD())));
        NamedTag tag = new NBTDeserializer(false).fromStream(dis);
        if (tag != null && tag.getTag() instanceof CompoundTag) {
            data = (CompoundTag) tag.getTag();
            initReferences(loadFlags);
        } else {
            throw new IOException("invalid data tag: " + (tag == null ? "null" : tag.getClass().getName()));
        }
    }

    public synchronized int getBiomeAt(int blockX, int blockY, int blockZ) {
        return biomes.getBiome(blockX, blockY, blockZ);
    }

    public synchronized void setBiomeAt(int blockX, int blockY, int blockZ, int biomeID) {
        biomes.setBiome(blockX, blockY, blockZ, biomeID);
    }

    int getBiomeIndex(int biomeX, int biomeY, int biomeZ) {
        return biomeY * 64 + biomeZ * 4 + biomeX;
    }

    public CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ) {
        int sectionIndex = MCAUtilSupport.blockToChunk(blockY);
        S section = sections.get(sectionIndex);
        if (section == null) {
            return null;
        }
        return section.getBlockStateAt(blockX, blockY, blockZ);
    }

    public void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {
        int sectionIndex = MCAUtilSupport.blockToChunk(blockY);
        S section = sections.get(sectionIndex);
        if (section == null) {
            section = config.newSectionFactory.get();
            sections.put(sectionIndex, section);
        }
        section.setBlockStateAt(blockX, blockY, blockZ, state, cleanup);
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    public int getLastMCAUpdate() {
        return lastMCAUpdate;
    }

    public void setLastMCAUpdate(int lastMCAUpdate) {
        this.lastMCAUpdate = lastMCAUpdate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public S getSection(int sectionY) {
        return sections.get(sectionY);
    }

    public void setSection(int sectionY, S section) {
        sections.put(sectionY, section);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public long getInhabitedTime() {
        return inhabitedTime;
    }

    public void setInhabitedTime(long inhabitedTime) {
        this.inhabitedTime = inhabitedTime;
    }

    public CompoundTag getHeightMaps() {
        return heightMaps;
    }

    public void setHeightMaps(CompoundTag heightMaps) {
        this.heightMaps = heightMaps;
    }

    public CompoundTag getCarvingMasks() {
        return carvingMasks;
    }

    public void setCarvingMasks(CompoundTag carvingMasks) {
        this.carvingMasks = carvingMasks;
    }

    public ListTag<CompoundTag> getEntities() {
        return entities;
    }

    public void setEntities(ListTag<CompoundTag> entities) {
        this.entities = entities;
    }

    public ListTag<CompoundTag> getTileEntities() {
        return tileEntities;
    }

    public void setTileEntities(ListTag<CompoundTag> tileEntities) {
        this.tileEntities = tileEntities;
    }

    public ListTag<CompoundTag> getTileTicks() {
        return tileTicks;
    }

    public void setTileTicks(ListTag<CompoundTag> tileTicks) {
        this.tileTicks = tileTicks;
    }

    public ListTag<CompoundTag> getLiquidTicks() {
        return liquidTicks;
    }

    public void setLiquidTicks(ListTag<CompoundTag> liquidTicks) {
        this.liquidTicks = liquidTicks;
    }

    public ListTag<ListTag<?>> getLights() {
        return lights;
    }

    public void setLights(ListTag<ListTag<?>> lights) {
        this.lights = lights;
    }

    public ListTag<ListTag<?>> getLiquidsToBeTicked() {
        return liquidsToBeTicked;
    }

    public void setLiquidsToBeTicked(ListTag<ListTag<?>> liquidsToBeTicked) {
        this.liquidsToBeTicked = liquidsToBeTicked;
    }

    public ListTag<ListTag<?>> getToBeTicked() {
        return toBeTicked;
    }

    public void setToBeTicked(ListTag<ListTag<?>> toBeTicked) {
        this.toBeTicked = toBeTicked;
    }

    public ListTag<ListTag<?>> getPostProcessing() {
        return postProcessing;
    }

    public void setPostProcessing(ListTag<ListTag<?>> postProcessing) {
        this.postProcessing = postProcessing;
    }

    public CompoundTag getStructures() {
        return structures;
    }

    public void setStructures(CompoundTag structures) {
        this.structures = structures;
    }

    int getBlockIndex(int blockX, int blockZ) {
        return (blockZ & 0xF) * 16 + (blockX & 0xF);
    }

    public void cleanupPalettesAndBlockStates() {
        for (S section : sections.values()) {
            if (section != null) {
                section.cleanupPaletteAndBlockStates();
            }
        }
    }

    public CompoundTag updateHandle(int xPos, int zPos) {
        data.putInt("DataVersion", dataVersion);
        CompoundTag level = data.getCompoundTag("Level");
        level.putInt("xPos", xPos);
        level.putInt("zPos", zPos);
        level.putLong("LastUpdate", lastUpdate);
        level.putLong("InhabitedTime", inhabitedTime);
        level.putIntArray("Biomes", biomes.getData());
        if (heightMaps != null) level.put("Heightmaps", heightMaps);
        if (carvingMasks != null) level.put("CarvingMasks", carvingMasks);
        if (entities != null) level.put("Entities", entities);
        if (tileEntities != null) level.put("TileEntities", tileEntities);
        if (tileTicks != null) level.put("TileTicks", tileTicks);
        if (liquidTicks != null) level.put("LiquidTicks", liquidTicks);
        if (lights != null) level.put("Lights", lights);
        if (liquidsToBeTicked != null) level.put("LiquidsToBeTicked", liquidsToBeTicked);
        if (toBeTicked != null) level.put("ToBeTicked", toBeTicked);
        if (postProcessing != null) level.put("PostProcessing", postProcessing);
        level.putString("Status", status);
        if (structures != null) level.put("Structures", structures);
        ListTag<CompoundTag> sectionList = new ListTag<>(CompoundTag.class);

        for (int i : sections.keySet()) {
            S section = sections.get(i);
            if (section != null) {
                sectionList.add(section.updateHandle(i));
            }
        }

        level.put("Sections", sectionList);
        return data;
    }

    public int sectionCount() {
        return sections.size();
    }

    public int getNativeIrisVersion() {
        return nativeIrisVersion;
    }

    public void setNativeIrisVersion(int nativeIrisVersion) {
        this.nativeIrisVersion = nativeIrisVersion;
    }
}
