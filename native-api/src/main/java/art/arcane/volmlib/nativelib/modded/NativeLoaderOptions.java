package art.arcane.volmlib.nativelib.modded;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public record NativeLoaderOptions(String modId, String blockBreakPermission, Predicate<BooleanSupplier> breakProbe) {
    public NativeLoaderOptions {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(blockBreakPermission, "blockBreakPermission");
        Objects.requireNonNull(breakProbe, "breakProbe");
    }
}
