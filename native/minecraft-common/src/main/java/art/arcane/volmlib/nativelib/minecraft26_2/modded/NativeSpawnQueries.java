package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.function.LongConsumer;

public final class NativeSpawnQueries {
    private NativeSpawnQueries() {
    }

    public static int surfaceHeight(NativeWorld world, int x, int z, boolean oceanFloor) {
        return level(world).getHeight(oceanFloor ? Heightmap.Types.OCEAN_FLOOR : Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    public static int light(NativeWorld world, int x, int y, int z) {
        return level(world).getMaxLocalRawBrightness(new BlockPos(x, y, z));
    }

    public static int livingEntities(NativeWorld world, int chunkX, int chunkZ) {
        ServerLevel level = level(world);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        AABB box = new AABB(baseX, level.getMinY(), baseZ, baseX + 16, level.getMaxY(), baseZ + 16);
        return level.getEntities((Entity) null, box, entity -> livingInChunk(entity, chunkX, chunkZ)).size();
    }

    public static Population population(NativeWorld world) {
        NaturalSpawner.SpawnState state = level(world).getChunkSource().getLastSpawnState();
        if (state == null) {
            return null;
        }
        Object2IntMap<MobCategory> counts = state.getMobCategoryCounts();
        int mobs = 0;
        for (MobCategory category : counts.keySet()) {
            mobs += counts.getInt(category);
        }
        return new Population(mobs, state.getSpawnableChunkCount());
    }

    public static void forEachReadyChunk(NativeWorld world, LongConsumer action) {
        level(world).getChunkSource().chunkMap.forEachReadyToSendChunk(chunk -> action.accept(chunk.getPos().pack()));
    }

    public static boolean solid(NativeBlockState state) {
        return NativeBlockProperties.isSolid((BlockState) state.nativeHandle());
    }

    public static boolean lava(NativeBlockState state) {
        return ((BlockState) state.nativeHandle()).is(Blocks.LAVA);
    }

    public static boolean water(NativeBlockState state) {
        BlockState nativeState = (BlockState) state.nativeHandle();
        return NativeBlockProperties.isWater(nativeState) || NativeBlockProperties.isWaterLogged(nativeState)
                || nativeState.is(Blocks.SEAGRASS) || nativeState.is(Blocks.TALL_SEAGRASS)
                || nativeState.is(Blocks.KELP) || nativeState.is(Blocks.KELP_PLANT);
    }

    public static boolean animalGround(NativeBlockState state) {
        BlockState nativeState = (BlockState) state.nativeHandle();
        return nativeState.is(Blocks.GRASS_BLOCK) || nativeState.is(Blocks.DIRT) || nativeState.is(Blocks.DIRT_PATH)
                || nativeState.is(Blocks.COARSE_DIRT) || nativeState.is(Blocks.ROOTED_DIRT) || nativeState.is(Blocks.PODZOL)
                || nativeState.is(Blocks.MYCELIUM) || nativeState.is(Blocks.SNOW_BLOCK);
    }

    private static boolean livingInChunk(Entity entity, int chunkX, int chunkZ) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        BlockPos position = entity.blockPosition();
        return (position.getX() >> 4) == chunkX && (position.getZ() >> 4) == chunkZ;
    }

    private static ServerLevel level(NativeWorld world) {
        return (ServerLevel) world.nativeHandle();
    }

    public record Population(int mobs, int spawnableChunks) {
    }
}
