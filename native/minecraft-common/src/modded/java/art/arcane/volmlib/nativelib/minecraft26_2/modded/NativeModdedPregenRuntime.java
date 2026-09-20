package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;

public final class NativeModdedPregenRuntime implements NativePregenRuntime {
    private static final TicketType PREGEN_TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    private static final ChunkLoad SUCCESS = new ChunkLoad(true, null);
    private final ServerLevel level;
    private final MinecraftServer server;

    private NativeModdedPregenRuntime(ServerLevel level) {
        this.level = level;
        server = level.getServer();
    }

    public static NativePregenRuntime from(NativeWorld world) {
        return new NativeModdedPregenRuntime((ServerLevel) world.nativeHandle());
    }

    public String worldIdentity() {
        return level.dimension().identifier().toString();
    }

    public File worldFolder() {
        return DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).toFile();
    }

    public boolean isServerThread() {
        return server.isSameThread();
    }

    public boolean isRunning() {
        return !server.isStopped() && server.isRunning();
    }

    public void execute(Runnable action) {
        server.execute(action);
    }

    public void save() {
        level.save(null, false, false);
    }

    public CompletableFuture<ChunkLoad> loadChunk(int x, int z) {
        ChunkPos position = new ChunkPos(x, z);
        CompletableFuture<?> loaded = CompletableFuture
                .supplyAsync(() -> level.getChunkSource().addTicketAndLoadWithRadius(PREGEN_TICKET, position, 0), server)
                .thenCompose((CompletableFuture<?> inner) -> inner);
        return loaded.thenApply(NativeModdedPregenRuntime::result);
    }

    public void releaseChunk(int x, int z) {
        server.execute(() -> level.getChunkSource().removeTicketWithRadius(PREGEN_TICKET, new ChunkPos(x, z), 0));
    }

    public String workerPoolDescription() {
        Executor executor = server.executor;
        if (executor == null) {
            return "unknown";
        }
        if (executor instanceof ThreadPoolExecutor pool) {
            return "ThreadPoolExecutor(core=" + pool.getCorePoolSize() + ",max=" + pool.getMaximumPoolSize() + ")";
        }
        if (executor instanceof ForkJoinPool pool) {
            return "ForkJoinPool(parallelism=" + pool.getParallelism() + ")";
        }
        return executor.getClass().getSimpleName();
    }

    public boolean supportsPauseWhenEmpty() {
        return server instanceof DedicatedServer;
    }

    public int pauseWhenEmptySeconds() {
        return server instanceof DedicatedServer dedicated ? dedicated.pauseWhenEmptySeconds() : 0;
    }

    public void pauseWhenEmptySeconds(int seconds) {
        if (server instanceof DedicatedServer dedicated) {
            dedicated.setPauseWhenEmptySeconds(seconds);
        }
    }

    public int playerCount() {
        return server.getPlayerCount();
    }

    private static ChunkLoad result(Object result) {
        return result instanceof ChunkResult<?> chunk && !chunk.isSuccess()
                ? new ChunkLoad(false, String.valueOf(chunk.getError())) : SUCCESS;
    }
}
