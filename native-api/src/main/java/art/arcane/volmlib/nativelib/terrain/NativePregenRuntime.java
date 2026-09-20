package art.arcane.volmlib.nativelib.terrain;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public interface NativePregenRuntime {
    String worldIdentity();
    File worldFolder();
    boolean isServerThread();
    boolean isRunning();
    void execute(Runnable action);
    void save();
    CompletableFuture<ChunkLoad> loadChunk(int x, int z);
    void releaseChunk(int x, int z);
    String workerPoolDescription();
    boolean supportsPauseWhenEmpty();
    int pauseWhenEmptySeconds();
    void pauseWhenEmptySeconds(int seconds);
    int playerCount();

    record ChunkLoad(boolean successful, String error) {
    }
}
