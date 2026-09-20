/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/**
 * Neutral view of a loaded world for edit and lifecycle paths; never used on the generation hot path.
 * <p>
 * Metadata reads ({@link #name()}, {@link #seed()}, the height bounds) are cheap and safe from any thread. The
 * block, biome and weather accessors read or mutate live world state and must be called on the thread that
 * owns the target chunk - the region thread on regionized platforms, the server thread elsewhere. Reach that
 * thread with the host scheduler.
 * <p>
 * Shared native world contract.
 */
public interface NativeWorld {
    /**
     * The host's world name. Never null.
     */
    String name();

    /**
     * The world seed, as the host reports it.
     */
    long seed();

    /**
     * Lowest buildable Y, inclusive. Usually negative.
     */
    int minHeight();

    /**
     * Highest buildable Y, exclusive.
     */
    int maxHeight();

    /**
     * The block state at world coordinates. Loads the chunk if it is not resident, so treat it as blocking.
     * Adapters delegate straight to the host, so a coordinate outside {@link #minHeight()}/{@link #maxHeight()}
     * gets whatever the host does with it - clamp, air, or throw. Bounds-check first.
     */
    NativeBlockState getBlock(int x, int y, int z);

    /**
     * Writes a block state at world coordinates. Same out-of-bounds caveat as {@link #getBlock(int, int, int)}.
     *
     * @param flags platform update flags; bit 0 requests neighbour/physics updates, higher bits are
     *              adapter-specific. Pass 0 for a silent write
     */
    void setBlock(int x, int y, int z, NativeBlockState block, int flags);

    /**
     * The biome at world coordinates. Loads the chunk if it is not resident.
     */
    NativeBiome getBiome(int x, int y, int z);

    /**
     * Whether the chunk is currently resident. Cheap; the only accessor here that never triggers a load.
     */
    boolean isChunkLoaded(int chunkX, int chunkZ);

    /**
     * The world's time of day in ticks.
     */
    long getTime();

    /**
     * Whether it is raining or snowing.
     */
    boolean isStorming();

    /**
     * Whether a thunderstorm is active.
     */
    boolean isThundering();

    /**
     * The adapter's backing world object - {@code org.bukkit.World} on Bukkit, {@code ServerLevel} on a mod
     * loader. Never null. Only code inside the owning adapter may cast it; core must not.
     */
    Object nativeHandle();
}
