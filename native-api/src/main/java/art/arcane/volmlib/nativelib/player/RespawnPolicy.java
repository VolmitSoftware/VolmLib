package art.arcane.volmlib.nativelib.player;

import java.util.Objects;

public record RespawnPolicy(RespawnPoint personal, boolean forced, RespawnPoint shared) {
    public RespawnPolicy {
        Objects.requireNonNull(shared, "shared");
    }
}
