package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPositionPredicate;
import art.arcane.volmlib.nativelib.terrain.NativeChunkWritePolicy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.function.Predicate;

public final class NativeGenerationWriteGuard {
    private NativeGenerationWriteGuard() {
    }

    public static boolean allowsDecoration(NativeChunkWritePolicy policy, WorldGenLevel region, ChunkPos center) {
        int radius = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).blockStateWriteRadius();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                int chunkX = Math.addExact(center.x(), offsetX);
                int chunkZ = Math.addExact(center.z(), offsetZ);
                ChunkAccess target = region.getChunk(chunkX, chunkZ);
                if (target.getPersistedStatus().isOrAfter(ChunkStatus.FULL)
                        && !policy.allowsNativeChunkWrite(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean allowsPendingStage(NativeChunkWritePolicy policy, ChunkAccess chunk, ChunkStatus stage) {
        return stage.isOrAfter(ChunkStatus.FEATURES)
                && chunk.getPersistedStatus().isOrAfter(ChunkStatus.NOISE)
                && !chunk.getPersistedStatus().isOrAfter(stage)
                && !policy.allowsNativeChunkWrite(chunk.getPos().x(), chunk.getPos().z());
    }

    public static Predicate<BlockPos> protectedPositions(NativeBlockPositionPredicate predicate) {
        return position -> predicate.test(position.getX(), position.getY(), position.getZ());
    }
}
