package art.arcane.volmlib.nativelib.chunk;

import java.util.Objects;

public record ChunkPacketSnapshot(ChunkPosition position, byte[] payload) {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public ChunkPacketSnapshot {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(payload, "payload");
        if (payload.length == 0 || payload.length > MAX_BYTES) {
            throw new IllegalArgumentException("Chunk packet payload length");
        }
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
