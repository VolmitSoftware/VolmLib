package art.arcane.volmlib.nativelib.terrain;

import java.util.Optional;

public interface NativeTerrainColumnPolicy {
    boolean usesFlatTerrain();

    int flatBaseHeight();

    FlatColumn flatColumn(int blockX, int blockZ);

    ColumnSession openQuery(Query query);

    String placementKey(String stateKey);

    record Query(int blockX, int blockZ, String operation) {
    }

    record FlatColumn(int floorY, String floorBlock) {
    }

    interface ColumnSession extends AutoCloseable {
        int runtimeId();

        Optional<? extends NativeBlockColumn> resolvedColumn();

        int height(boolean ignoreFluid);

        @Override
        void close();
    }
}
