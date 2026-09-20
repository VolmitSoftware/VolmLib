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

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public final class NativeChunkRegeneration {
    private final ServerLevel level;

    public NativeChunkRegeneration(NativeWorld world) {
        level = (ServerLevel) world.nativeHandle();
    }

    public void apply(Replacement replacement, Runnable afterBlocks) {
        LevelChunk chunk = level.getChunk(replacement.chunkX(), replacement.chunkZ());
        ChunkPos pos = chunk.getPos();
        discardEntities(pos);
        for (BlockPos blockEntityPos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(blockEntityPos);
        }

        int dimMinY = replacement.minY();
        int height = replacement.height();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        BlockState airState = Blocks.AIR.defaultBlockState();
        ThreadedLevelLightEngine lightEngine = (ThreadedLevelLightEngine) level.getChunkSource().getLightEngine();
        List<BlockPos> lightChecks = new ArrayList<>();

        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            LevelChunkSection section = chunk.getSection(i);
            int sectionBaseY = chunk.getSectionYFromSectionIndex(i) << 4;
            section.acquire();
            try {
                for (int y = 0; y < 16; y++) {
                    int worldY = sectionBaseY + y;
                    int bufferY = worldY - dimMinY;
                    boolean inRange = bufferY >= 0 && bufferY < height;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockState target = airState;
                            if (inRange) {
                                NativeBlockState block = replacement.blocks().get(x, bufferY, z);
                                if (block != null) {
                                    target = (BlockState) block.nativeHandle();
                                }
                            }
                            BlockState previous = section.setBlockState(x, y, z, target, false);
                            if (previous != target && (previous.getLightEmission() != target.getLightEmission()
                                    || previous.getLightDampening() != target.getLightDampening()
                                    || previous.propagatesSkylightDown() != target.propagatesSkylightDown())) {
                                lightChecks.add(new BlockPos(baseX + x, worldY, baseZ + z));
                            }
                            if (target.hasBlockEntity() && target.getBlock() instanceof EntityBlock entityBlock) {
                                BlockEntity entity = entityBlock.newBlockEntity(new BlockPos(baseX + x, worldY, baseZ + z), target);
                                if (entity != null) {
                                    chunk.setBlockEntity(entity);
                                }
                            }
                        }
                    }
                }
            } finally {
                section.release();
            }
            lightEngine.updateSectionStatus(SectionPos.of(pos, chunk.getSectionYFromSectionIndex(i)), section.hasOnlyAir());
        }

        afterBlocks.run();
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
        replacement.biomes().fill(chunk, level);
        chunk.markUnsaved();

        for (BlockPos check : lightChecks) {
            lightEngine.checkBlock(check);
        }

        for (ServerPlayer tracking : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
            tracking.connection.chunkSender.dropChunk(tracking, pos);
            tracking.connection.chunkSender.markChunkPendingToSend(chunk);
        }
    }

    private void discardEntities(ChunkPos pos) {
        AABB box = new AABB(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxY() + 1, pos.getMaxBlockZ() + 1);
        for (Entity entity : level.getEntities((Entity) null, box, (Entity e) -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
    }


    public record Replacement(int chunkX, int chunkZ, int minY, int height,
                              BlockProvider blocks, NativeBiomeResolver biomes) {
    }

    @FunctionalInterface
    public interface BlockProvider {
        NativeBlockState get(int x, int y, int z);
    }
}
