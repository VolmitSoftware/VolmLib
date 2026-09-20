package art.arcane.volmlib.nativelib.terrain;

import org.bukkit.NamespacedKey;
import java.io.IOException;

public interface NativeTerrainPipelinePolicy<R extends NativeGenerationRoute> {
    NativeGenerationScope acquireStage(String operation);
    NativeGenerationLease acquireLease(String operation);
    R openRoute(int chunkX, int chunkZ, String operation);
    NativeGenerationScope openContext(NativeGenerationLease lease);
    boolean allowsChunkWrite(int chunkX, int chunkZ);
    boolean usesFlatTerrain();
    int minimumY();
    int terrainHeight(int blockX, int blockZ, boolean floor);
    NamespacedKey naturalTerrainKey();
    byte[] naturalTerrainReceipt(R route) throws IOException;
}
