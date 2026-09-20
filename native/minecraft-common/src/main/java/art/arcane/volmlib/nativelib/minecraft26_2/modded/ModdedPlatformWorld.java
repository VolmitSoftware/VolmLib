/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBiome;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public final class ModdedPlatformWorld implements NativeWorld {
    private final ServerLevel level;

    public ModdedPlatformWorld(ServerLevel level) {
        this.level = level;
    }

    @Override
    public String name() {
        return level.dimension().identifier().toString();
    }

    @Override
    public long seed() {
        return level.getSeed();
    }

    @Override
    public int minHeight() {
        return level.getMinY();
    }

    @Override
    public int maxHeight() {
        return exclusiveMaxHeight(level.getMinY(), level.getHeight());
    }

    @Override
    public NativeBlockState getBlock(int x, int y, int z) {
        return ModdedBlockState.of(level.getBlockState(new BlockPos(x, y, z)), null);
    }

    @Override
    public void setBlock(int x, int y, int z, NativeBlockState block, int flags) {
        level.setBlock(new BlockPos(x, y, z), (BlockState) block.nativeHandle(), flags);
    }

    @Override
    public NativeBiome getBiome(int x, int y, int z) {
        Holder<Biome> biome = level.getBiome(new BlockPos(x, y, z));
        String key = biome.unwrapKey()
                .map(resourceKey -> resourceKey.identifier().toString())
                .orElse("minecraft:plains");
        return ModdedBiome.of(biome.value(), key);
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    @Override
    public long getTime() {
        return level.getDefaultClockTime();
    }

    @Override
    public boolean isStorming() {
        return level.isRaining();
    }

    @Override
    public boolean isThundering() {
        return level.isThundering();
    }

    @Override
    public Object nativeHandle() {
        return level;
    }

    public ServerLevel level() {
        return level;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModdedPlatformWorld world && level == world.level;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(level);
    }

    static int exclusiveMaxHeight(int minHeight, int height) {
        return minHeight + height;
    }
}
