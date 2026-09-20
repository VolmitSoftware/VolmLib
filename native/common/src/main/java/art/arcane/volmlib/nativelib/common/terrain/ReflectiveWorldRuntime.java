package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;
import art.arcane.volmlib.nativelib.terrain.NativeWorldClock;
import art.arcane.volmlib.nativelib.terrain.NativeWorkerPool;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeOptions;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public abstract class ReflectiveWorldRuntime implements NativeWorldRuntime {
    private final NativeWorkerPool workers = new RuntimeWorkerPool();
    private final NativeWorldClock clock = new RuntimeWorldClock();
    private final RuntimeCapabilities capabilities = RuntimeCapabilities.probe();

    public NativeWorkerPool workers() { return workers; }
    public NativeWorldClock clock() { return clock; }
    public boolean available() { return capabilities.hasPaperLikeRuntime(); }
    public String flavor() { return capabilities.paperLikeFlavor().name().toLowerCase(Locale.ROOT); }
    public String description() { return capabilities.paperLikeResolution(); }
    public boolean supportsRegistryAccess() { return capabilities.serverRegistryAccessMethod() != null; }
    public boolean supportsUnloadAsync() { return capabilities.unloadWorldAsyncMethod() != null; }
    public boolean isGlobalTickThread() { return RuntimeOperations.isGlobalTickThread(); }

    protected abstract Object createLevelStem(Object registryAccess, NamespacedKey dimensionTypeKey);

    public World create(WorldRuntimeOptions options, WorldRuntimeExecution execution) throws ReflectiveOperationException {
        RuntimeOperations operations = new RuntimeOperations(execution);
        Object storageAccess = null;
        try {
            operations.stageRuntimeConfiguration(options.worldName(), options.generatorId());
            Object stemKey = operations.createRuntimeLevelStemKey(options.worldKey());
            if (capabilities.paperLikeFlavor() == RuntimeCapabilities.PaperLikeFlavor.CURRENT_INFO_AND_DATA) {
                Object dimensionKey = operations.createDimensionKey(stemKey);
                Object loadedWorldData = capabilities.paperWorldDataMethod().invoke(null, capabilities.minecraftServer(), dimensionKey, options.worldName());
                Object worldLoadingInfo = capabilities.worldLoadingInfoConstructor().newInstance(options.environment(), stemKey, dimensionKey, options.persistent());
                Object worldLoadingInfoAndData = capabilities.worldLoadingInfoAndDataConstructor().newInstance(worldLoadingInfo, loadedWorldData);
                Object worldDataAndGenSettings = operations.createCurrentWorldDataAndSettings(capabilities, options.worldName());
                if (!options.existingWorldData()) {
                    worldDataAndGenSettings = operations.applySeedToWorldDataAndGenSettings(worldDataAndGenSettings, options.seed());
                }
                Object levelStem = options.dimensionTypeKey() == null
                        ? operations.resolveConfiguredLevelStem(worldDataAndGenSettings)
                        : createLevelStem(operations.getRuntimeServerRegistryAccess(capabilities), options.dimensionTypeKey());
                capabilities.createLevelMethod().invoke(capabilities.minecraftServer(), levelStem, worldLoadingInfoAndData, worldDataAndGenSettings);
            } else {
                Object levelStem = options.dimensionTypeKey() == null
                        ? operations.resolveDefaultLevelStem(capabilities, options.worldName())
                        : createLevelStem(operations.getRuntimeServerRegistryAccess(capabilities), options.dimensionTypeKey());
                storageAccess = operations.createLegacyStorageAccess(capabilities, options.levelRoot());
                Object primaryLevelData = operations.createLegacyPrimaryLevelData(capabilities, storageAccess, options.worldName());
                Object worldLoadingInfo = capabilities.worldLoadingInfoConstructor().newInstance(0, options.worldName(), options.environment().name().toLowerCase(Locale.ROOT), stemKey, options.persistent());
                capabilities.createLevelMethod().invoke(capabilities.minecraftServer(), levelStem, worldLoadingInfo, storageAccess, primaryLevelData);
            }
            return WorldIdentity.resolve(options.worldKey()).orElseThrow(() -> new IllegalStateException(
                    "Native runtime backend did not load world \"" + options.worldName() + "\"."));
        } finally {
            operations.closeLevelStorageAccess(storageAccess);
        }
    }

    public CompletableFuture<Boolean> unload(World world, boolean save, WorldRuntimeExecution execution) {
        return new RuntimeOperations(execution).unloadWorldAsync(capabilities, world, save);
    }
}
