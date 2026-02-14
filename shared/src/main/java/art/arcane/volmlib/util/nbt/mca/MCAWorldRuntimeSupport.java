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

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class MCAWorldRuntimeSupport<M, C extends MCAChunkLike, S extends MCASectionLike> {
    public interface ChunkAccess<M, C extends MCAChunkLike> {
        C getChunk(M mca, int chunkX, int chunkZ);

        void setChunk(M mca, int chunkX, int chunkZ, C chunk);

        C createChunk();
    }

    public interface SectionAccess<C extends MCAChunkLike, S extends MCASectionLike> {
        S getSection(C chunk, int sectionY);

        void setSection(C chunk, int sectionY, S section);

        S createSection();
    }

    private final BiFunction<Integer, Integer, M> mcaProvider;
    private final ChunkAccess<M, C> chunkAccess;
    private final SectionAccess<C, S> sectionAccess;

    public MCAWorldRuntimeSupport(
            BiFunction<Integer, Integer, M> mcaProvider,
            ChunkAccess<M, C> chunkAccess,
            SectionAccess<C, S> sectionAccess
    ) {
        this.mcaProvider = mcaProvider;
        this.chunkAccess = chunkAccess;
        this.sectionAccess = sectionAccess;
    }

    public CompoundTag getBlockStateTag(int x, int y, int z) {
        S section = getChunkSection(x >> 4, y >> 4, z >> 4);
        return section.getBlockStateAt(x & 15, y & 15, z & 15);
    }

    public void setBlockStateTag(int x, int y, int z, CompoundTag state, boolean cleanup) {
        S section = getChunkSection(x >> 4, y >> 4, z >> 4);
        section.setBlockStateAt(x & 15, y & 15, z & 15, state, cleanup);
    }

    public void setBiomeId(int x, int y, int z, int biomeId) {
        getChunk(x >> 4, z >> 4).setBiomeAt(x & 15, y, z & 15, biomeId);
    }

    public S getChunkSection(int x, int y, int z) {
        C chunk = getChunk(x, z);
        S section = sectionAccess.getSection(chunk, y);

        if (section == null) {
            section = sectionAccess.createSection();
            sectionAccess.setSection(chunk, y, section);
        }

        return section;
    }

    public C getChunk(int x, int z) {
        return getChunk(mcaProvider.apply(x >> 5, z >> 5), x, z);
    }

    public C getChunk(M mca, int x, int z) {
        C chunk = chunkAccess.getChunk(mca, x & 31, z & 31);
        if (chunk == null) {
            chunk = chunkAccess.createChunk();
            chunkAccess.setChunk(mca, x & 31, z & 31, chunk);
        }
        return chunk;
    }

    public C getNewChunk(M mca, int x, int z) {
        C chunk = chunkAccess.createChunk();
        chunkAccess.setChunk(mca, x & 31, z & 31, chunk);
        return chunk;
    }
}
