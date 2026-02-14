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

import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.io.IOException;
import java.io.RandomAccessFile;

public interface MCAChunkLike {
    void deserialize(RandomAccessFile raf, long loadFlags) throws IOException;

    int serialize(RandomAccessFile raf, int xPos, int zPos) throws IOException;

    int getLastMCAUpdate();

    void setBiomeAt(int blockX, int blockY, int blockZ, int biomeID);

    int getBiomeAt(int blockX, int blockY, int blockZ);

    void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup);

    CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ);
}
