package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeTerrainSnapshots;
import art.arcane.volmlib.nativelib.terrain.SnapshotPolicy;
import ca.spottedleaf.concurrentutil.completable.Completable;
import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

public final class NativeTerrainSnapshotsImpl implements NativeTerrainSnapshots {
    private static final long WRITE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final int SNAPSHOT_BATCH_SIZE = 64;

    @Override
    public <T> CompletableFuture<T> capture(CaptureRequest request, SnapshotPolicy<T> policy) {
        World world = request.world();
        int chunkX = request.chunkX();
        int chunkZ = request.chunkZ();
        SnapshotPolicy.CaptureTarget target = new SnapshotPolicy.CaptureTarget(world.getWorldFolder().toPath(),
                chunkX, chunkZ, request.minimumY(), request.height());
        ServerLevel level = ((CraftWorld) world).getHandle();
        CompletableFuture<CapturedChunk> snapshot = new CompletableFuture<>();
        policy.runRegion(new SnapshotPolicy.Region(world, chunkX, chunkZ), () -> {
            try {
                NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(chunkX, chunkZ);
                ChunkAccess chunk = holder == null ? null : holder.getCurrentChunk();
                if (chunk == null) {
                    snapshot.complete(new CapturedChunk(new ChunkPos(chunkX, chunkZ), CompletableFuture.completedFuture(null)));
                    return;
                }
                String status = BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString();
                if (!policy.hasTerrain(status)) {
                    throw new IOException("Native chunk " + chunkX + "," + chunkZ + " has no completed terrain at " + status);
                }
                snapshot.complete(scheduleSnapshot(level, chunk));
            } catch (Throwable failure) {
                snapshot.completeExceptionally(failure);
            }
        }).whenComplete((ignored, failure) -> {
            if (failure != null) {
                snapshot.completeExceptionally(failure);
            }
        });
        return snapshot.thenApplyAsync(captured -> {
            try {
                long deadline = System.nanoTime() + WRITE_TIMEOUT_NANOS;
                CompoundTag data = awaitSnapshot(level, captured, deadline);
                Checkpoint checkpoint = checkpoint(chunkX, chunkZ, data, policy);
                awaitWrite(level, checkpoint.chunk(), deadline);
                if (data == null) {
                    return policy.readPersisted(target);
                }
                verifyCheckpoint(world.getWorldFolder().toPath(), checkpoint, policy);
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

    @Override
    public CompletableFuture<Void> flush(World world, SnapshotPolicy<?> policy) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        List<NewChunkHolder> holders = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolders();
        return CompletableFuture.runAsync(() -> {
            try {
                flushSnapshots(world, level, holders, policy);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        });
    }

    static void flushSnapshots(World world, ServerLevel level, List<NewChunkHolder> holders, SnapshotPolicy<?> policy) throws IOException {
        long deadline = System.nanoTime() + WRITE_TIMEOUT_NANOS;
        List<Checkpoint> checkpoints = new ArrayList<>(holders.size());
        for (int offset = 0; offset < holders.size(); offset += SNAPSHOT_BATCH_SIZE) {
            int end = Math.min(holders.size(), offset + SNAPSHOT_BATCH_SIZE);
            List<CompletableFuture<CapturedChunk>> snapshots = requestSnapshots(world, level, holders.subList(offset, end), policy);
            awaitSnapshots(snapshots, deadline);
            for (CompletableFuture<CapturedChunk> future : snapshots) {
                CapturedChunk captured = future.join();
                CompoundTag data = awaitSnapshot(level, captured, deadline);
                checkpoints.add(checkpoint(captured.chunk().x(), captured.chunk().z(), data, policy));
            }
            for (int index = offset; index < end; index++) {
                awaitWrite(level, checkpoints.get(index).chunk(), deadline);
            }
        }
        finishCheckpoint(level, world.getWorldFolder().toPath(), checkpoints, policy);
    }

    private static List<CompletableFuture<CapturedChunk>> requestSnapshots(
            World world, ServerLevel level, List<NewChunkHolder> holders, SnapshotPolicy<?> policy) {
        List<CompletableFuture<CapturedChunk>> snapshots = new ArrayList<>(holders.size());
        for (NewChunkHolder original : holders) {
            ChunkPos position = new ChunkPos(original.chunkX, original.chunkZ);
            CompletableFuture<CapturedChunk> snapshot = new CompletableFuture<>();
            snapshots.add(snapshot);
            policy.runRegion(new SnapshotPolicy.Region(world, position.x(), position.z()), () -> {
                try {
                    NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager
                            .getChunkHolder(position.x(), position.z());
                    ChunkAccess chunk = holder == null ? null : holder.getCurrentChunk();
                    snapshot.complete(chunk != null && chunk.isUnsaved() ? scheduleSnapshot(level, chunk)
                            : new CapturedChunk(position, CompletableFuture.completedFuture(null)));
                } catch (Throwable failure) {
                    snapshot.completeExceptionally(failure);
                }
            }).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    snapshot.completeExceptionally(failure);
                }
            });
        }
        return snapshots;
    }

    private static void awaitSnapshots(List<CompletableFuture<CapturedChunk>> snapshots, long deadlineNanos)
            throws IOException {
        try {
            CompletableFuture.allOf(snapshots.toArray(CompletableFuture[]::new))
                    .get(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while copying terrain checkpoint chunks.", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new IOException("Unable to copy terrain checkpoint chunks.", failure);
        }
    }

    static void finishCheckpoint(ServerLevel level, Path worldFolder, List<Checkpoint> checkpoints, SnapshotPolicy<?> policy) throws IOException {
        long deadline = System.nanoTime() + WRITE_TIMEOUT_NANOS;
        for (Checkpoint checkpoint : checkpoints) {
            awaitWrite(level, checkpoint.chunk(), deadline);
        }
        MoonriseRegionFileIO.flushRegionStorages(level, MoonriseRegionFileIO.RegionFileType.CHUNK_DATA);
        for (Checkpoint checkpoint : checkpoints) {
            verifyCheckpoint(worldFolder, checkpoint, policy);
        }
    }

    static Checkpoint checkpoint(int chunkX, int chunkZ, CompoundTag data, SnapshotPolicy<?> policy) {
        if (data == null) {
            return new Checkpoint(new ChunkPos(chunkX, chunkZ), null);
        }
        byte[] receipt = data.getCompound("ChunkBukkitValues")
                .flatMap(values -> values.getByteArray(policy.receiptKey())).orElse(null);
        long structureActivation = data.getCompound("ChunkBukkitValues").map(values -> values.getLongOr(
                policy.activationKey(), 0)).orElse(0L);
        return new Checkpoint(new ChunkPos(chunkX, chunkZ), new Verification(
                data.getStringOr("Status", ""), receipt, structureActivation));
    }

    private static void verifyCheckpoint(Path worldFolder, Checkpoint checkpoint, SnapshotPolicy<?> policy) throws IOException {
        Verification expected = checkpoint.verification();
        if (expected == null) {
            return;
        }
        policy.verifyCheckpoint(worldFolder, new SnapshotPolicy.CheckpointData(checkpoint.chunk().x(), checkpoint.chunk().z(),
                expected.status(), expected.receipt(), expected.structureActivation()));
    }

    static void awaitWrite(ServerLevel level, ChunkPos chunk, long deadlineNanos) throws IOException {
        ChunkTaskScheduler scheduler = level.moonrise$getChunkTaskScheduler();
        while (true) {
            checkWriteWait(chunk, deadlineNanos);
            if (MoonriseRegionFileIO.getPriority(level, chunk.x(), chunk.z(),
                    MoonriseRegionFileIO.RegionFileType.CHUNK_DATA) == Priority.COMPLETING) {
                return;
            }
            helpSaveTasks(scheduler);
        }
    }

    static CapturedChunk scheduleSnapshot(ServerLevel level, ChunkAccess chunk) {
        SerializableChunkData snapshot = SerializableChunkData.copyOf(level, chunk);
        PlatformHooks.get().chunkSyncSave(level, chunk, snapshot);
        Completable<CompoundTag> nativeData = new Completable<>();
        CompletableFuture<CompoundTag> data = new CompletableFuture<>();
        PrioritisedExecutor.PrioritisedTask task = level.moonrise$getChunkTaskScheduler().saveExecutor
                .createTask(() -> serializeSnapshot(snapshot, nativeData, data));
        task.queue();
        ChunkPos position = chunk.getPos();
        MoonriseRegionFileIO.scheduleSave(level, position.x(), position.z(), nativeData, task,
                MoonriseRegionFileIO.RegionFileType.CHUNK_DATA, Priority.NORMAL);
        return new CapturedChunk(position, data);
    }

    static CompoundTag awaitSnapshot(ServerLevel level, CapturedChunk captured, long deadlineNanos) throws IOException {
        ChunkTaskScheduler scheduler = level.moonrise$getChunkTaskScheduler();
        while (!captured.data().isDone()) {
            checkWriteWait(captured.chunk(), deadlineNanos);
            helpSaveTasks(scheduler);
        }
        try {
            return captured.data().join();
        } catch (CompletionException failure) {
            throw new IOException("Unable to serialize terrain checkpoint chunk " + captured.chunk(), failure.getCause());
        }
    }

    private static void serializeSnapshot(SerializableChunkData snapshot, Completable<CompoundTag> nativeData,
                                          CompletableFuture<CompoundTag> data) {
        try {
            CompoundTag serialized = snapshot.write();
            nativeData.complete(serialized);
            data.complete(serialized);
        } catch (Throwable failure) {
            nativeData.completeExceptionally(failure);
            data.completeExceptionally(failure);
        }
    }

    private static void checkWriteWait(ChunkPos chunk, long deadlineNanos) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Interrupted while saving terrain checkpoint chunk " + chunk);
        }
        if (System.nanoTime() - deadlineNanos >= 0L) {
            throw new IOException("Timed out saving terrain checkpoint chunk " + chunk);
        }
    }

    private static void helpSaveTasks(ChunkTaskScheduler scheduler) {
        boolean helped = scheduler.saveExecutor.executeTask();
        helped |= scheduler.compressionExecutor.executeTask();
        if (!helped) {
            LockSupport.parkNanos(1_000_000L);
        }
    }

    record CapturedChunk(ChunkPos chunk, CompletableFuture<CompoundTag> data) {
    }

    record Checkpoint(ChunkPos chunk, Verification verification) {
    }

    record Verification(String status, byte[] receipt, long structureActivation) {
    }
}
