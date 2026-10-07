package art.arcane.volmlib.integration;

import java.util.Objects;

public record ReloadPreparation(boolean ready, String reason) {
    public ReloadPreparation {
        reason = Objects.requireNonNull(reason, "reason").trim();
        if (!ready && reason.isEmpty()) {
            throw new IllegalArgumentException("A reload refusal requires a reason");
        }
        if (ready && !reason.isEmpty()) {
            throw new IllegalArgumentException("A ready reload cannot include a refusal reason");
        }
    }

    public static ReloadPreparation readyToUnload() {
        return new ReloadPreparation(true, "");
    }

    public static ReloadPreparation refuse(String reason) {
        return new ReloadPreparation(false, reason);
    }
}
