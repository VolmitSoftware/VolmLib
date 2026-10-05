package art.arcane.volmlib.nativelib.chunk;

import java.util.Objects;

public record ChunkWorldContext(String dimension, String dimensionType, long seed, boolean debug, boolean flat,
                                int seaLevel, int minY, int height, long gameTime, long clockTime, float rain, float thunder) {
    public ChunkWorldContext {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(dimensionType, "dimensionType");
        if (dimension.isEmpty() || dimensionType.isEmpty() || height <= 0 || height > 4096 || (height & 15) != 0
            || (minY & 15) != 0 || !Float.isFinite(rain) || !Float.isFinite(thunder) || rain < 0 || rain > 1
            || thunder < 0 || thunder > 1) {
            throw new IllegalArgumentException("Chunk world context");
        }
    }
}
