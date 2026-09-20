package art.arcane.volmlib.nativelib.chunk;

import art.arcane.volmlib.nativelib.NativeBinding;

import java.util.OptionalDouble;

@NativeBinding("chunk.NativeChunkSendRateAccessor")
public interface ChunkSendRateAccessor {
    boolean available();

    String describe();

    OptionalDouble read(ChunkSendRateLimit limit);

    boolean write(ChunkSendRateLimit limit, double value);
}
