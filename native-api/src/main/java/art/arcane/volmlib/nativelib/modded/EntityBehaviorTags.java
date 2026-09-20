package art.arcane.volmlib.nativelib.modded;

import java.util.Objects;

public record EntityBehaviorTags(String nonPersistent, String unaware) {
    public EntityBehaviorTags {
        Objects.requireNonNull(nonPersistent, "nonPersistent");
        Objects.requireNonNull(unaware, "unaware");
    }
}
