package art.arcane.volmlib.nativelib.chunk;

public enum ChunkSendRateLimit {
    SEND,
    LOAD;

    public String label() {
        return switch (this) {
            case SEND -> "send";
            case LOAD -> "load";
        };
    }
}
