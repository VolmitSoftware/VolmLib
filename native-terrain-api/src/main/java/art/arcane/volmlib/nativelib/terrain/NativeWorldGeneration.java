package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@NativeBinding("terrain.NativeWorldGenerationImpl")
public interface NativeWorldGeneration {
    <C, D, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>, R extends NativeGenerationRoute>
    void inject(World world, NativeBukkitGeneratorContext<C, D, P, O, R> context) throws NoSuchFieldException, IllegalAccessException;
    Dimension dimension(World world);
    ScopeResult scope(ScopeRequest request) throws NoSuchFieldException, IllegalAccessException;
    CompletableFuture<Void> completeBootstrap(World world) throws NoSuchFieldException, IllegalAccessException;
    void abandonBootstrap(World world);
    NativeWorldLifecycleFactory.Controller lifecycle(NativeWorldLifecyclePolicy policy);
    boolean missingDimensionTypes(List<NamespacedKey> keys);
    String generationRendererIdentity();

    record Dimension(String key, int minimumY, int height, int logicalHeight, int worldMinimumY, int worldHeight) { }
    record ScopeRequest(World world, StructureScope scope, Set<String> declaredSources,
                        StructureFrequencyControl control, ScopeMode mode) { }
    record ScopeResult(int retainedManagedSets, int excludedManagedSets) { }
    enum ScopeMode { IMMEDIATE, RETAINED, AUTHORING }
}
