package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.TerrainSnapshotPolicy;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import java.nio.file.Path;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin.ChunkMapTerrainReceiptAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ModdedTerrainSnapshots {
    private final ServerLevel level;

    private ModdedTerrainSnapshots(ServerLevel level) {
        this.level = level;
    }

    public static ModdedTerrainSnapshots forWorld(NativeWorld world) {
        if (world == null || !(world.nativeHandle() instanceof ServerLevel level)) {
            throw new IllegalStateException("The level is unavailable for terrain capture.");
        }
        return new ModdedTerrainSnapshots(level);
    }

    public <T> CompletableFuture<T> capture(TerrainSnapshotPolicy.CaptureTarget target, TerrainSnapshotPolicy<T> policy) {
        int chunkX = target.chunkX();
        int chunkZ = target.chunkZ();
        CompletableFuture<CompoundTag> snapshot = new CompletableFuture<>();
        runOwned(level, () -> {
            try {
                ChunkMap map = level.getChunkSource().chunkMap;
                ChunkHolder holder = map.getUpdatingChunkIfPresent(ChunkPos.pack(chunkX, chunkZ));
                ChunkAccess chunk = holder == null ? null : holder.getLatestChunk();
                if (chunk == null) {
                    map.read(new ChunkPos(chunkX, chunkZ)).whenComplete((data, failure) -> {
                        if (failure != null) {
                            snapshot.completeExceptionally(failure);
                        } else {
                            snapshot.complete(data.orElse(null));
                        }
                    });
                    return;
                }
                String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                if (!policy.hasTerrain(status)) {
                    throw new IOException("Native chunk " + chunkX + "," + chunkZ + " has no terrain at " + status);
                }
                CompoundTag data = SerializableChunkData.copyOf(level, chunk).write();
                if (chunk.isUnsaved()) {
                    ((ChunkMapTerrainReceiptAccess) map).volmlib$saveTerrainCheckpoint(chunk);
                }
                snapshot.complete(data);
            } catch (Throwable failure) {
                snapshot.completeExceptionally(failure);
            }
        });
        return snapshot.thenApplyAsync(data -> {
            try {
                if (data == null) {
                    throw new IOException("Saved native chunk is unavailable: " + chunkX + "," + chunkZ);
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (DataOutputStream output = new DataOutputStream(bytes)) {
                    NbtIo.write(data, output);
                }
                return policy.decodeNbt(bytes.toByteArray(), target);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    public CompletableFuture<Void> flush(Path worldFolder, TerrainSnapshotPolicy<?> policy) {
        CompletableFuture<Void> complete = new CompletableFuture<>();
        runOwned(level, () -> {
            try {
                ChunkMap map = level.getChunkSource().chunkMap;
                List<Checkpoint> checkpoints = new ArrayList<>();
                for (ChunkHolder holder : ((ChunkMapTerrainReceiptAccess) map).volmlib$updatingChunks().values()) {
                    ChunkAccess chunk = holder.getLatestChunk();
                    if (chunk == null) {
                        continue;
                    }
                    String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                    if (!policy.hasTerrain(status)
                            && NativeTerrainReceipts.structureActivation(chunk) == 0) {
                        continue;
                    }
                    checkpoints.add(new Checkpoint(chunk.getPos().x(), chunk.getPos().z(), status,
                            NativeTerrainReceipts.get(chunk), NativeTerrainReceipts.structureActivation(chunk)));
                    if (chunk.isUnsaved()) {
                        ((ChunkMapTerrainReceiptAccess) map).volmlib$saveTerrainCheckpoint(chunk);
                    }
                }
                map.synchronize(true).thenRunAsync(() -> verifyCheckpoints(worldFolder, checkpoints, policy))
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                complete.completeExceptionally(failure);
                            } else {
                                complete.complete(null);
                            }
                        });
            } catch (Throwable failure) {
                complete.completeExceptionally(failure);
            }
        });
        return complete;
    }

    private static void verifyCheckpoints(Path worldFolder, List<Checkpoint> checkpoints, TerrainSnapshotPolicy<?> policy) {
        try {
            for (Checkpoint checkpoint : checkpoints) {
                policy.verifyCheckpoint(worldFolder, new TerrainSnapshotPolicy.CheckpointData(
                        checkpoint.chunkX(), checkpoint.chunkZ(), checkpoint.status(), checkpoint.receipt(),
                        checkpoint.structureActivation()));
            }
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }

    private record Checkpoint(int chunkX, int chunkZ, String status, byte[] receipt, long structureActivation) {
    }

    private static void runOwned(ServerLevel level, Runnable action) {
        if (level.getServer().isSameThread()) {
            action.run();
        } else {
            level.getServer().execute(action);
        }
    }
}
