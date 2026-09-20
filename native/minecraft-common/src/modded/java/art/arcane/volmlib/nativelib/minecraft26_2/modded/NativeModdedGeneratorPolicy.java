package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import java.util.List;
import art.arcane.volmlib.nativelib.terrain.structure.StructureInjectionPolicy;
import art.arcane.volmlib.nativelib.terrain.StructureFrequencyControl;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;
import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationRoute;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeChunkWritePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnSelection;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnBiomePolicy;
import art.arcane.volmlib.nativelib.terrain.feature.NativeFeatureTable;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import java.io.IOException;
import java.util.concurrent.Executor;

public interface NativeModdedGeneratorPolicy<C, P extends StructureStartPlan, O extends StructureOwnershipRecordView<O>> {
    <H, S> NativeModdedBiomePolicy<H, S> biomePolicy(NativeBiomeSourceAccess<H, S> source);
    FeatureStage<C> featureStage(NativeFeatureBiomeSource source);
    NativeModdedStructureStage.Policy<C, P, O> structures();
    NativeSpawnBiomePolicy<C> spawns();
    NativeModdedServer server();
    C current();
    C current(NativeWorld world);
    C current(String worldKey);
    C queryCurrent(String operation);
    NativeGenerationRoute route(C context, Position position, String operation);
    NativeGenerationScope coordinateScope(C context, Position position, String operation);
    NativeGenerationLease lease(C context, String operation);
    NativeGenerationScope context(C context, long sessionId);
    NativeChunkWritePolicy chunkWrites(C context);
    boolean allowsNewGeneration(C context, int chunkX, int chunkZ);
    boolean historyBypass(C context);
    boolean stacked(C context);
    int runtimeId(C context);
    int terrainHeight(C context, int x, int z, boolean ignoreFluid, boolean host);
    NativeBlockColumn resolvedColumn(C context, int x, int z);
    String placementKey(String key);
    Terrain generate(C context, int chunkX, int chunkZ) throws Exception;
    void generated(C context, int chunkX, int chunkZ);
    void generating(int chunkX, int chunkZ);
    RuntimeException generationFailure(C context, Position chunk, Throwable failure);
    byte[] terrainReceipt(NativeGenerationRoute route) throws IOException;
    long structureActivation(NativeGenerationRoute route);
    boolean parallelChunkSystem();
    Executor executor();
    NativeSpawnSelection spawnSelection(C context, SpawnQuery query);
    StructureReferencePolicy<P, O> structurePolicy(C context);
    StructureInjectionPolicy<P> injectionPolicy(C context);
    NativeGenerationLease terrainLease(C context) throws Exception;
    NativeGenerationLease queryLease(C context, String operation) throws Exception;
    RuntimeException queryFailure(String operation, Throwable failure);
    void addDebugInformation(List<String> info);
    StructureFrequencyControl structureFrequencies();
    int depth();
    int seaLevel();
    int minimumY();
    int spawnHeight(int minimumY, int height);
    boolean allowsNativeChunkWrite(int chunkX, int chunkZ);

    record Position(int x, int z) {}
    record SpawnQuery(int x, int y, int z, String visibleBiomeKey) {}
    record Terrain(int minimumY, int height, BlockBuffer blocks) {}
    interface BlockBuffer { NativeBlockState rawOrNull(int x, int y, int z); }
    interface FeatureStage<C> {
        NativeModdedImportedFeatures nativeFeatures();
        NativeFeatureTable currentTable();
        void prepare(C context);
        NativeFeatureTable placementTable(C context);
    }
}
