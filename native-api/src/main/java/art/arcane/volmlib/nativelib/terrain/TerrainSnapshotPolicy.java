package art.arcane.volmlib.nativelib.terrain;

import java.io.IOException;
import java.nio.file.Path;

public interface TerrainSnapshotPolicy<T> {
    boolean hasTerrain(String status);

    String receiptKey();

    String activationKey();

    T readPersisted(CaptureTarget target) throws IOException;

    T decodeNbt(byte[] bytes, CaptureTarget target) throws IOException;

    void verifyCheckpoint(Path worldFolder, CheckpointData checkpoint) throws IOException;

    record CaptureTarget(Path worldFolder, int chunkX, int chunkZ, int minimumY, int height) {
    }

    record CheckpointData(int chunkX, int chunkZ, String status, byte[] receipt, long activation) {
    }
}
