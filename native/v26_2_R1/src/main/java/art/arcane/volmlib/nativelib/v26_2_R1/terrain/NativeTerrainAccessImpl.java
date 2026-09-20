package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.bukkit.World;
import art.arcane.volmlib.nativelib.minecraft26_2.terrain.NativeStructureGenerationException;
import art.arcane.volmlib.nativelib.terrain.BukkitTerrainBuffer;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockVolume;
import art.arcane.volmlib.nativelib.terrain.NativeTerrainAccess;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.generator.CraftChunkData;
import org.bukkit.generator.ChunkGenerator.ChunkData;

public class NativeTerrainAccessImpl extends NativeBiomeAccessImpl implements NativeTerrainAccess {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");

    @Override
    public boolean applyChunkBlocks(Chunk bukkitChunk, BukkitTerrainBuffer data) {
        if (!(data.getChunkData() instanceof CraftChunkData chunkData)) {
            return false;
        }

        try {
            ServerLevel level = ((CraftWorld) bukkitChunk.getWorld()).getHandle();
            LevelChunk chunk = level.getChunk(bukkitChunk.getX(), bukkitChunk.getZ());
            ChunkAccess source = chunkData.getHandle();
            removeBlockEntities(chunk);

            int minY = level.getMinY();
            int baseX = chunk.getPos().getMinBlockX();
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection target = chunk.getSection(i);
                LevelChunkSection from = source.getSection(i);
                if (from.hasOnlyAir() && target.hasOnlyAir()) {
                    continue;
                }

                int sectionBaseY = minY + (i << 4);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = from.getBlockState(x, y, z);
                            target.setBlockState(x, y, z, state, false);
                            if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock entityBlock) {
                                BlockPos pos = new BlockPos(baseX + x, sectionBaseY + y, baseZ + z);
                                BlockEntity entity = entityBlock.newBlockEntity(pos, state);
                                if (entity != null) {
                                    chunk.setBlockEntity(entity);
                                }
                            }
                        }
                    }
                }
            }

            finishChunkRewrite(level, chunk);
            return true;
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native terrain write failed", e);
            return false;
        }
    }

    @Override
    public boolean applyChunkDataBlocks(ChunkData chunkData, NativeBlockVolume data) {
        if (!(chunkData instanceof CraftChunkData craftChunkData)) {
            return false;
        }

        try {
            ChunkAccess access = craftChunkData.getHandle();
            int minY = craftChunkData.getMinHeight();
            int height = craftChunkData.getMaxHeight() - minY;
            int accessMinY = access.getMinY();
            int accessMaxY = accessMinY + access.getHeight();
            int yStart = Math.max(0, accessMinY - minY);
            int yEnd = Math.min(height, accessMaxY - minY);
            int baseX = access.getPos().getMinBlockX();
            int baseZ = access.getPos().getMinBlockZ();
            for (int z = 0; z < 16; z++) {
                for (int y = yStart; y < yEnd; y++) {
                    int blockY = y + minY;
                    int sectionIndex = (blockY - accessMinY) >> 4;
                    LevelChunkSection section = access.getSection(sectionIndex);
                    int sectionY = blockY & 15;
                    for (int x = 0; x < 16; x++) {
                        NativeBlockState platformState = data.getStoredRaw(x, y, z);
                        if (platformState == null) {
                            continue;
                        }

                        BlockData blockData = (BlockData) platformState.placementHandle();
                        if (!(blockData instanceof CraftBlockData craftBlockData)) {
                            return false;
                        }

                        BlockState state = craftBlockData.getState();
                        BlockState oldState = section.setBlockState(x, sectionY, z, state, false);
                        if (state.hasBlockEntity()) {
                            BlockPos pos = new BlockPos(baseX + x, blockY, baseZ + z);
                            BlockEntity entity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                            if (entity == null) {
                                access.removeBlockEntity(pos);
                            } else {
                                access.setBlockEntity(entity);
                            }
                        } else if (oldState != null && oldState.hasBlockEntity()) {
                            access.removeBlockEntity(new BlockPos(baseX + x, blockY, baseZ + z));
                        }
                    }
                }
            }

            return true;
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native terrain write failed", e);
            return false;
        }
    }
    @Override
    public int[] placeStructure(World world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            Registry<Structure> reg = registry().lookupOrThrow(Registries.STRUCTURE);
            Structure structure = reg.getValue(Identifier.parse(structureKey));
            if (structure == null) {
                return null;
            }
            Holder<Structure> holder = reg.wrapAsHolder(structure);
            RandomState randomState = level.getChunkSource().randomState();
            StructureTemplateManager templateManager = level.getStructureManager();
            StructureManager structureManager = level.structureManager();
            BiomeSource biomeSource = generator.getBiomeSource();
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

            StructureStart start = structure.generate(
                    holder,
                    level.dimension(),
                    level.registryAccess(),
                    generator,
                    biomeSource,
                    randomState,
                    templateManager,
                    seed,
                    chunkPos,
                    0,
                    level,
                    biome -> true);

            if (start == null || !start.isValid()) {
                return null;
            }

            BoundingBox box = start.getBoundingBox();
            int spanX = box.maxX() - box.minX() + 1;
            int spanY = box.maxY() - box.minY() + 1;
            int spanZ = box.maxZ() - box.minZ() + 1;
            if (maxSpan > 0 && (spanX > maxSpan || spanZ > maxSpan || spanY > maxSpan)) {
                return null;
            }

            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed));
            int minCX = box.minX() >> 4;
            int maxCX = box.maxX() >> 4;
            int minCZ = box.minZ() >> 4;
            int maxCZ = box.maxZ() >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    level.getChunk(cx, cz);
                    ChunkPos cp = new ChunkPos(cx, cz);
                    BoundingBox chunkBox = new BoundingBox(
                            cp.getMinBlockX(), box.minY(), cp.getMinBlockZ(),
                            cp.getMaxBlockX(), box.maxY(), cp.getMaxBlockZ());
                    start.placeInChunk(level, structureManager, generator, random, chunkBox, cp);
                }
            }
            return new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
        } catch (RuntimeException e) {
            throw NativeStructureGenerationException.failure(
                    "capture placement", structureKey, chunkX, chunkZ, e);
        }
    }

}
