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

import art.arcane.volmlib.util.nbt.mca.palette.MCAPaletteAccess;
import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;

import java.util.function.Supplier;

public class MCASectionSupport {
    private CompoundTag data;
    private MCAPaletteAccess palette;
    private byte[] blockLight;
    private byte[] skyLight;

    public MCASectionSupport(CompoundTag sectionRoot, long loadFlags, Supplier<MCAPaletteAccess> paletteFactory) {
        data = sectionRoot;
        ListTag<?> rawPalette = sectionRoot.getListTag("Palette");
        if (rawPalette == null) {
            return;
        }

        palette = paletteFactory.get();
        palette.readFromSection(sectionRoot);
        ByteArrayTag blockLight = sectionRoot.getByteArrayTag("BlockLight");
        ByteArrayTag skyLight = sectionRoot.getByteArrayTag("SkyLight");
        this.blockLight = blockLight != null ? blockLight.getValue() : null;
        this.skyLight = skyLight != null ? skyLight.getValue() : null;
    }

    public static MCASectionSupport createNew(Supplier<MCAPaletteAccess> paletteFactory) {
        MCASectionSupport section = new MCASectionSupport();
        section.data = new CompoundTag();
        section.palette = paletteFactory.get();
        return section;
    }

    private MCASectionSupport() {
    }

    public boolean isEmpty() {
        return data == null;
    }

    public synchronized CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ) {
        synchronized (palette) {
            return palette.getBlock(blockX & 15, blockY & 15, blockZ & 15);
        }
    }

    public synchronized void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {
        synchronized (palette) {
            palette.setBlock(blockX & 15, blockY & 15, blockZ & 15, state);
        }
    }

    public void cleanupPaletteAndBlockStates() {
    }

    public synchronized byte[] getBlockLight() {
        return blockLight;
    }

    public synchronized void setBlockLight(byte[] blockLight) {
        if (blockLight != null && blockLight.length != 2048) {
            throw new IllegalArgumentException("BlockLight array must have a length of 2048");
        }
        this.blockLight = blockLight;
    }

    public synchronized byte[] getSkyLight() {
        return skyLight;
    }

    public synchronized void setSkyLight(byte[] skyLight) {
        if (skyLight != null && skyLight.length != 2048) {
            throw new IllegalArgumentException("SkyLight array must have a length of 2048");
        }
        this.skyLight = skyLight;
    }

    public synchronized CompoundTag updateHandle(int y) {
        data.putByte("Y", (byte) y);

        if (palette != null) {
            synchronized (palette) {
                palette.writeToSection(data);
            }
        }

        if (blockLight != null) {
            data.putByteArray("BlockLight", blockLight);
        }

        if (skyLight != null) {
            data.putByteArray("SkyLight", skyLight);
        }

        return data;
    }
}
