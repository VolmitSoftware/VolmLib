package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.terrain.feature.NativeImportedFeaturePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStagePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureCachePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureVolumePolicy;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.NamespacedKey;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public interface NativeBukkitGeneratorContext<C, D, P extends StructureStartPlan,
        O extends StructureOwnershipRecordView<O>, R extends NativeGenerationRoute>
        extends NativeTerrainPipelinePolicy<R> {
    C current();
    <H, V> NativeBiomeSourcePolicy<H> biomes(NativeBiomeRegistry<H, V> registry);
    NativeImportedFeaturePolicy<D> importedFeatures();
    NativeStructureBootstrapPolicy bootstrap();
    NativeTerrainColumnPolicy columns();
    StructureStagePolicy<C, P, O> structures();
    StructureCachePolicy<D> structureCache();
    StructureInjectionPolicy<P> injection();
    StructureVolumePolicy<C, P> volumes();
    void installVolumes(VolumeResolver<C> resolver);
    void onRetirement(IntConsumer listener);
    int fluidHeight();
    int runtimeId();
    boolean generateStructures();
    boolean allowsNewChunk(int chunkX, int chunkZ);
    boolean active();
    boolean stacked();
    NativeGenerationScope openCoordinateScope(int blockX, int blockZ, String operation);
    NativeSpawnSelection spawnSelection(int blockX, int worldY, int blockZ, String physicalBiomeKey);
    <T> T admittedSpawns(Supplier<T> operation, T fallback);
    NamespacedKey structureActivationKey();
    long activationId(R route);
    void failed(String operation, Throwable failure);

    @FunctionalInterface
    interface VolumeResolver<C> {
        KList<NativeStructureVolume> volumesAt(C context, int chunkX, int chunkZ);
    }
}
