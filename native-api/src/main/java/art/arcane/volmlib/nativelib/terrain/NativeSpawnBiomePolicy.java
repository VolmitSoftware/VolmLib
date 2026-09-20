package art.arcane.volmlib.nativelib.terrain;

import java.util.function.Consumer;

public interface NativeSpawnBiomePolicy<C> {
    C current();
    int runtimeId(C context);
    NativeGenerationLease lease(C context);
    NativeGenerationScope context(C context, NativeGenerationLease lease);
    void populate(C context, MappingTarget target);

    @FunctionalInterface
    interface MappingTarget {
        Consumer<String> vanilla(String key);
    }
}
