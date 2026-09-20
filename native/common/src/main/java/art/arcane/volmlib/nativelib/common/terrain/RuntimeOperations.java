package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class RuntimeOperations {
    private final WorldRuntimeExecution execution;

    RuntimeOperations(WorldRuntimeExecution execution) {
        this.execution = execution;
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException && invocationTargetException.getCause() != null) {
            return unwrap(invocationTargetException.getCause());
        }
        if (throwable instanceof java.util.concurrent.CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        if (throwable instanceof ExecutionException executionException && executionException.getCause() != null) {
            return unwrap(executionException.getCause());
        }
        return throwable;
    }

    Object invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        return method.invoke(target, args);
    }

    Object invokeNamed(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    Object read(Field field, Object target) throws IllegalAccessException {
        return field.get(target);
    }

    void stageRuntimeConfiguration(String worldName, String generatorId) throws ReflectiveOperationException {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            throw new IllegalStateException("Bukkit server is unavailable.");
        }

        Field configurationField = RuntimeResolution.resolveField(bukkitServer.getClass(), "configuration");
        Object rawConfiguration = configurationField.get(bukkitServer);
        if (!(rawConfiguration instanceof YamlConfiguration configuration)) {
            throw new IllegalStateException("CraftServer configuration field is unavailable.");
        }

        ConfigurationSection worldsSection = configuration.getConfigurationSection("worlds");
        if (worldsSection == null) {
            worldsSection = configuration.createSection("worlds");
        }

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
        if (worldSection == null) {
            worldSection = worldsSection.createSection(worldName);
        }

        worldSection.set("generator", generatorId);
    }

    Object getRuntimeDatapackDimensions(RuntimeCapabilities capabilities) throws ReflectiveOperationException {
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Method datapackDimensionsMethod = RuntimeResolution.resolveMethod(worldLoaderContext.getClass(), "datapackDimensions", method -> method.getParameterCount() == 0);
        if (datapackDimensionsMethod == null) {
            throw new IllegalStateException("DataLoadContext does not expose datapackDimensions().");
        }
        Object datapackDimensions = datapackDimensionsMethod.invoke(worldLoaderContext);
        if (datapackDimensions == null) {
            throw new IllegalStateException("DataLoadContext.datapackDimensions() returned null.");
        }
        return datapackDimensions;
    }

    Object getRuntimeServerRegistryAccess(RuntimeCapabilities capabilities) throws ReflectiveOperationException {
        Method registryAccessMethod = capabilities.serverRegistryAccessMethod();
        if (registryAccessMethod == null) {
            throw new IllegalStateException("MinecraftServer does not expose registryAccess().");
        }
        Object registryAccess = registryAccessMethod.invoke(capabilities.minecraftServer());
        if (registryAccess == null) {
            throw new IllegalStateException("MinecraftServer.registryAccess() returned null.");
        }
        return registryAccess;
    }

    Object getRuntimeLevelStemRegistry(RuntimeCapabilities capabilities) throws ReflectiveOperationException {
        Object datapackDimensions = getRuntimeDatapackDimensions(capabilities);
        Object levelStemRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Method lookupMethod = RuntimeResolution.resolveMethod(datapackDimensions.getClass(), "lookupOrThrow", method -> method.getParameterCount() == 1);
        if (lookupMethod == null) {
            throw new IllegalStateException("Registry access does not expose lookupOrThrow(...).");
        }
        return lookupMethod.invoke(datapackDimensions, levelStemRegistryKey);
    }

    Object createRuntimeLevelStemKey(NamespacedKey worldKey) throws ReflectiveOperationException {
        Object rawIdentifier = Class.forName("net.minecraft.resources.Identifier")
                .getMethod("fromNamespaceAndPath", String.class, String.class)
                .invoke(null, worldKey.getNamespace(), worldKey.getKey());
        Object registryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("LEVEL_STEM")
                .get(null);
        Method createMethod = Class.forName("net.minecraft.resources.ResourceKey")
                .getMethod("create", registryKey.getClass(), rawIdentifier.getClass());
        return createMethod.invoke(null, registryKey, rawIdentifier);
    }

    Object createDimensionKey(Object stemKey) throws ReflectiveOperationException {
        Class<?> resourceKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
        Method identifierMethod = RuntimeResolution.resolveMethod(resourceKeyClass, "identifier", method -> method.getParameterCount() == 0);
        Object identifier = identifierMethod.invoke(stemKey);
        Object dimensionRegistryKey = Class.forName("net.minecraft.core.registries.Registries")
                .getField("DIMENSION")
                .get(null);
        Method createMethod = resourceKeyClass.getMethod("create", dimensionRegistryKey.getClass(), identifier.getClass());
        return createMethod.invoke(null, dimensionRegistryKey, identifier);
    }

    Object resolveConfiguredLevelStem(Object worldDataAndGenSettings) throws ReflectiveOperationException {
        Object overworldKey = Class.forName("net.minecraft.world.level.dimension.LevelStem").getField("OVERWORLD").get(null);
        return resolveConfiguredLevelStem(worldDataAndGenSettings, overworldKey);
    }

    static Object resolveConfiguredLevelStem(Object worldDataAndGenSettings, Object key) throws ReflectiveOperationException {
        Method settingsMethod = RuntimeResolution.resolveMethod(worldDataAndGenSettings.getClass(), "genSettings", method -> method.getParameterCount() == 0);
        Object settings = settingsMethod.invoke(worldDataAndGenSettings);
        Method dimensionsMethod = RuntimeResolution.resolveMethod(settings.getClass(), "dimensions", method -> method.getParameterCount() == 0);
        Object dimensions = dimensionsMethod.invoke(settings);
        Object stem = lookupRegistryValue(dimensions, key);
        if (stem == null) {
            throw new IllegalStateException("Configured world generation settings contain no overworld level stem.");
        }
        return stem;
    }

    Object resolveDefaultLevelStem(RuntimeCapabilities capabilities, String worldName) throws ReflectiveOperationException {
        try {
            Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
            Object overworldKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                    .getField("OVERWORLD")
                    .get(null);
            return lookupRegistryValue(levelStemRegistry, overworldKey);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to resolve fallback OVERWORLD LevelStem from datapack registry access for world \"" + worldName + "\".", unwrap(e));
        }
    }

    static Object lookupRegistryValue(Object registry, Object key) throws ReflectiveOperationException {
        Method getValueMethod = RuntimeResolution.resolveMethod(registry.getClass(), "getValue", method -> method.getParameterCount() == 1
                && method.getParameterTypes()[0].isInstance(key));
        if (getValueMethod != null) {
            Object resolved = getValueMethod.invoke(registry, key);
            if (resolved != null) {
                return resolved;
            }
        }

        Method getMethod = RuntimeResolution.resolveMethod(registry.getClass(), "get", method -> method.getParameterCount() == 1
                && method.getParameterTypes()[0].isInstance(key));
        if (getMethod == null) {
            throw new IllegalStateException("Unable to resolve OVERWORLD LevelStem from registry.");
        }
        Object raw = getMethod.invoke(registry, key);
        return extractRegistryValue(raw);
    }

    static Object extractRegistryValue(Object raw) throws ReflectiveOperationException {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Optional<?> optional) {
            Object nested = optional.orElse(null);
            if (nested == null) {
                return null;
            }
            return extractRegistryValue(nested);
        }
        Method valueMethod = RuntimeResolution.resolveMethod(raw.getClass(), "value", method -> method.getParameterCount() == 0);
        if (valueMethod != null) {
            return valueMethod.invoke(raw);
        }
        return raw;
    }

    void applyWorldDataNameAndModInfo(RuntimeCapabilities capabilities, Object worldDataAndGenSettings, String worldName) throws ReflectiveOperationException {
        Method dataMethod = RuntimeResolution.resolveMethod(worldDataAndGenSettings.getClass(), "data", method -> method.getParameterCount() == 0);
        if (dataMethod == null) {
            return;
        }

        Object worldData = dataMethod.invoke(worldDataAndGenSettings);
        if (worldData == null) {
            return;
        }

        Method checkNameMethod = RuntimeResolution.resolveMethod(worldData.getClass(), "checkName", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        if (checkNameMethod != null) {
            checkNameMethod.invoke(worldData, worldName);
        }

        Method getModdedStatusMethod = RuntimeResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getModdedStatus", method -> method.getParameterCount() == 0);
        Method getServerModNameMethod = RuntimeResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getServerModName", method -> method.getParameterCount() == 0);
        if (getModdedStatusMethod == null || getServerModNameMethod == null) {
            return;
        }

        Object modCheck = getModdedStatusMethod.invoke(capabilities.minecraftServer());
        Method shouldReportAsModifiedMethod = RuntimeResolution.resolveMethod(modCheck.getClass(), "shouldReportAsModified", method -> method.getParameterCount() == 0);
        Method setModdedInfoMethod = RuntimeResolution.resolveMethod(worldData.getClass(), "setModdedInfo", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 2 && String.class.equals(params[0]) && boolean.class.equals(params[1]);
        });
        if (shouldReportAsModifiedMethod == null || setModdedInfoMethod == null) {
            return;
        }

        boolean modified = Boolean.TRUE.equals(shouldReportAsModifiedMethod.invoke(modCheck));
        String modName = (String) getServerModNameMethod.invoke(capabilities.minecraftServer());
        setModdedInfoMethod.invoke(worldData, modName, modified);
    }

    static Object applySeedToWorldDataAndGenSettings(Object worldDataAndGenSettings, long seed) throws ReflectiveOperationException {
        Method genSettingsMethod = RuntimeResolution.resolveMethod(worldDataAndGenSettings.getClass(), "genSettings", method -> method.getParameterCount() == 0);
        Method dataMethod = RuntimeResolution.resolveMethod(worldDataAndGenSettings.getClass(), "data", method -> method.getParameterCount() == 0);
        if (genSettingsMethod == null || dataMethod == null) {
            throw new IllegalStateException("WorldDataAndGenSettings does not expose data()/genSettings().");
        }

        Object genSettings = genSettingsMethod.invoke(worldDataAndGenSettings);
        Method optionsMethod = RuntimeResolution.resolveMethod(genSettings.getClass(), "options", method -> method.getParameterCount() == 0);
        Method dimensionsMethod = RuntimeResolution.resolveMethod(genSettings.getClass(), "dimensions", method -> method.getParameterCount() == 0);
        if (optionsMethod == null || dimensionsMethod == null) {
            throw new IllegalStateException("WorldGenSettings does not expose options()/dimensions().");
        }

        Object options = optionsMethod.invoke(genSettings);
        Method seedMethod = RuntimeResolution.resolveMethod(options.getClass(), "seed", method -> method.getParameterCount() == 0 && long.class.equals(method.getReturnType()));
        Method withSeedMethod = RuntimeResolution.resolveMethod(options.getClass(), "withSeed", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && OptionalLong.class.equals(params[0]);
        });
        if (seedMethod == null || withSeedMethod == null) {
            throw new IllegalStateException("WorldOptions does not expose seed()/withSeed(OptionalLong).");
        }

        long currentSeed = (long) seedMethod.invoke(options);
        if (currentSeed == seed) {
            return worldDataAndGenSettings;
        }

        Object newOptions = withSeedMethod.invoke(options, OptionalLong.of(seed));
        Object newGenSettings = construct(genSettings.getClass(), newOptions, dimensionsMethod.invoke(genSettings));
        return construct(worldDataAndGenSettings.getClass(), dataMethod.invoke(worldDataAndGenSettings), newGenSettings);
    }

    private static Object construct(Class<?> type, Object first, Object second) throws ReflectiveOperationException {
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length != 2) {
                continue;
            }

            if ((first == null || params[0].isInstance(first)) && (second == null || params[1].isInstance(second))) {
                constructor.setAccessible(true);
                return constructor.newInstance(first, second);
            }
        }

        throw new IllegalStateException("No compatible two-argument constructor on " + type.getName());
    }

    Object createCurrentWorldDataAndSettings(RuntimeCapabilities capabilities, String worldName) throws ReflectiveOperationException {
        Object settings = read(capabilities.settingsField(), capabilities.minecraftServer());
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
        boolean demo = Boolean.TRUE.equals(capabilities.isDemoMethod().invoke(capabilities.minecraftServer()));
        Object options = read(capabilities.optionsField(), capabilities.minecraftServer());
        Method hasMethod = RuntimeResolution.resolveMethod(options.getClass(), "has", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        boolean bonusChest = hasMethod != null && Boolean.TRUE.equals(hasMethod.invoke(options, "bonusChest"));
        Object dataLoadOutput = capabilities.createNewWorldDataMethod().invoke(null, settings, worldLoaderContext, levelStemRegistry, demo, bonusChest);
        Method cookieMethod = RuntimeResolution.resolveMethod(dataLoadOutput.getClass(), "cookie", method -> method.getParameterCount() == 0);
        if (cookieMethod == null) {
            throw new IllegalStateException("WorldLoader.DataLoadOutput does not expose cookie().");
        }
        Object worldDataAndGenSettings = cookieMethod.invoke(dataLoadOutput);
        applyWorldDataNameAndModInfo(capabilities, worldDataAndGenSettings, worldName);
        return worldDataAndGenSettings;
    }

    Object createLegacyPrimaryLevelData(RuntimeCapabilities capabilities, Object levelStorageAccess, String worldName) throws ReflectiveOperationException {
        Object levelDataResult = capabilities.paperWorldDataMethod().invoke(null, levelStorageAccess);
        Method fatalErrorMethod = RuntimeResolution.resolveMethod(levelDataResult.getClass(), "fatalError", method -> method.getParameterCount() == 0);
        Method dataTagMethod = RuntimeResolution.resolveMethod(levelDataResult.getClass(), "dataTag", method -> method.getParameterCount() == 0);
        if (fatalErrorMethod != null && Boolean.TRUE.equals(fatalErrorMethod.invoke(levelDataResult))) {
            throw new IllegalStateException("Paper runtime world-data helper reported a fatal error for \"" + worldName + "\".");
        }
        if (dataTagMethod != null && dataTagMethod.invoke(levelDataResult) != null) {
            throw new IllegalStateException("Runtime world \"" + worldName + "\" already contains level data.");
        }

        Object settings = read(capabilities.settingsField(), capabilities.minecraftServer());
        Object worldLoaderContext = read(capabilities.worldLoaderContextField(), capabilities.minecraftServer());
        Object levelStemRegistry = getRuntimeLevelStemRegistry(capabilities);
        boolean demo = Boolean.TRUE.equals(capabilities.isDemoMethod().invoke(capabilities.minecraftServer()));
        Object options = read(capabilities.optionsField(), capabilities.minecraftServer());
        Method hasMethod = RuntimeResolution.resolveMethod(options.getClass(), "has", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        boolean bonusChest = hasMethod != null && Boolean.TRUE.equals(hasMethod.invoke(options, "bonusChest"));
        Object dataLoadOutput = capabilities.createNewWorldDataMethod().invoke(null, settings, worldLoaderContext, levelStemRegistry, demo, bonusChest);
        Method cookieMethod = RuntimeResolution.resolveMethod(dataLoadOutput.getClass(), "cookie", method -> method.getParameterCount() == 0);
        if (cookieMethod == null) {
            throw new IllegalStateException("WorldLoader.DataLoadOutput does not expose cookie().");
        }
        Object primaryLevelData = cookieMethod.invoke(dataLoadOutput);

        Method checkNameMethod = RuntimeResolution.resolveMethod(primaryLevelData.getClass(), "checkName", method -> {
            Class<?>[] params = method.getParameterTypes();
            return params.length == 1 && String.class.equals(params[0]);
        });
        if (checkNameMethod != null) {
            checkNameMethod.invoke(primaryLevelData, worldName);
        }

        Method getModdedStatusMethod = RuntimeResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getModdedStatus", method -> method.getParameterCount() == 0);
        Method getServerModNameMethod = RuntimeResolution.resolveMethod(capabilities.minecraftServer().getClass(), "getServerModName", method -> method.getParameterCount() == 0);
        if (getModdedStatusMethod != null && getServerModNameMethod != null) {
            Object modCheck = getModdedStatusMethod.invoke(capabilities.minecraftServer());
            Method shouldReportAsModifiedMethod = RuntimeResolution.resolveMethod(modCheck.getClass(), "shouldReportAsModified", method -> method.getParameterCount() == 0);
            Method setModdedInfoMethod = RuntimeResolution.resolveMethod(primaryLevelData.getClass(), "setModdedInfo", method -> {
                Class<?>[] params = method.getParameterTypes();
                return params.length == 2 && String.class.equals(params[0]) && boolean.class.equals(params[1]);
            });
            if (shouldReportAsModifiedMethod != null && setModdedInfoMethod != null) {
                boolean modified = Boolean.TRUE.equals(shouldReportAsModifiedMethod.invoke(modCheck));
                String modName = (String) getServerModNameMethod.invoke(capabilities.minecraftServer());
                setModdedInfoMethod.invoke(primaryLevelData, modName, modified);
            }
        }

        return primaryLevelData;
    }

    Object createLegacyStorageAccess(RuntimeCapabilities capabilities, File levelRoot) throws ReflectiveOperationException {
        Class<?> levelStorageSourceClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource");
        Method createDefaultMethod = levelStorageSourceClass.getMethod("createDefault", Path.class);
        Object levelStorageSource = createDefaultMethod.invoke(null, levelRoot.getParentFile().toPath());
        Method storageAccessMethod = capabilities.levelStorageAccessMethod();
        if (storageAccessMethod.getParameterCount() == 1) {
            return storageAccessMethod.invoke(levelStorageSource, levelRoot.getName());
        }
        Object overworldStemKey = Class.forName("net.minecraft.world.level.dimension.LevelStem")
                .getField("OVERWORLD")
                .get(null);
        return storageAccessMethod.invoke(levelStorageSource, levelRoot.getName(), overworldStemKey);
    }

    void closeLevelStorageAccess(Object levelStorageAccess) {
        if (levelStorageAccess == null) {
            return;
        }
        try {
            Method closeMethod = levelStorageAccess.getClass().getMethod("close");
            closeMethod.invoke(levelStorageAccess);
        } catch (Throwable failure) {
            execution.reportFailure("Failed to close the level storage access; the world session lock may still be held.",
                    unwrap(failure));
        }
    }

    CompletableFuture<Boolean> unloadWorldAsync(RuntimeCapabilities capabilities, World world, boolean save) {
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable invokeTask = () -> beginUnload(capabilities, world, save, result);
        boolean folia = execution.regionized();
        if ((!folia && execution.primaryThread()) || (folia && isGlobalTickThread())) {
            invokeTask.run();
            return result;
        }

        CompletableFuture<Void> scheduled = runGlobalAsync(invokeTask);
        scheduled.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrap(throwable));
            }
        });
        return result;
    }

    private void beginUnload(
            RuntimeCapabilities capabilities,
            World world,
            boolean save,
            CompletableFuture<Boolean> result
    ) {
        CompletableFuture<Boolean> operation;
        try {
            operation = unloadWorldViaAsyncApi(capabilities, world, save);
            if (operation == null) {
                operation = unloadWorldWithoutAsyncApi(capabilities, world, save);
            }
        } catch (Throwable e) {
            result.completeExceptionally(unwrap(e));
            return;
        }

        operation.whenComplete((unloaded, throwable) -> {
            if (throwable == null) {
                result.complete(Boolean.TRUE.equals(unloaded));
            } else {
                result.completeExceptionally(unwrap(throwable));
            }
        });
    }

    private CompletableFuture<Boolean> unloadWorldWithoutAsyncApi(
            RuntimeCapabilities capabilities,
            World world,
            boolean save
    ) {
        String worldName = world.getName();
        try {
            if (capabilities.minecraftServer() == null || capabilities.removeLevelMethod() == null) {
                return CompletableFuture.completedFuture(Bukkit.unloadWorld(world, save));
            }

            if (!announceManualWorldUnload(world)) {
                return CompletableFuture.completedFuture(false);
            }

            if (save) {
                world.save();
            }
            Method getHandleMethod = world.getClass().getMethod("getHandle");
            Object serverLevel = getHandleMethod.invoke(world);
            CompletableFuture<Boolean> operation = detachServerLevelAsync(capabilities, serverLevel, world)
                    .thenCompose(unused -> drainChunkTasksAsync(world, serverLevel))
                    .thenCompose(unused -> closeServerLevelAsync(world, serverLevel))
                    .thenApply(unused -> WorldIdentity.resolve(WorldIdentity.key(world)).isEmpty());
            return contextualizeUnloadFailure(worldName, operation);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Failed to unload world \"" + worldName + "\" through the selected world lifecycle backend.",
                    unwrap(e)
            ));
        }
    }

    static boolean announceManualWorldUnload(World world) {
        WorldUnloadEvent unloadEvent = new WorldUnloadEvent(world);
        Bukkit.getPluginManager().callEvent(unloadEvent);
        return !unloadEvent.isCancelled();
    }

    private CompletableFuture<Boolean> unloadWorldViaAsyncApi(RuntimeCapabilities capabilities, World world, boolean save) {
        if (capabilities.unloadWorldAsyncMethod() == null || capabilities.bukkitServer() == null) {
            return null;
        }

        return invokeAsyncUnload(
                capabilities.bukkitServer(),
                capabilities.unloadWorldAsyncMethod(),
                world,
                save
        );
    }

    static CompletableFuture<Boolean> invokeAsyncUnload(
            Object bukkitServer,
            Method unloadWorldAsyncMethod,
            World world,
            boolean save
    ) {
        CompletableFuture<Boolean> callbackFuture = new CompletableFuture<>();
        Consumer<Object> callback = unloaded -> {
            try {
                callbackFuture.complete(asyncUnloadSucceeded(unloaded));
            } catch (Throwable failure) {
                callbackFuture.completeExceptionally(unwrap(failure));
            }
        };
        try {
            unloadWorldAsyncMethod.invoke(bukkitServer, world, save, callback);
        } catch (Throwable e) {
            callbackFuture.completeExceptionally(unwrap(e));
        }
        return callbackFuture;
    }

    private static boolean asyncUnloadSucceeded(Object result) throws ReflectiveOperationException {
        if (result instanceof Boolean unloaded) {
            return unloaded;
        }
        if (result == null) {
            return false;
        }
        Method isSuccessMethod = result.getClass().getMethod("isSuccess");
        return Boolean.TRUE.equals(isSuccessMethod.invoke(result));
    }

    private CompletableFuture<Boolean> contextualizeUnloadFailure(
            String worldName,
            CompletableFuture<Boolean> operation
    ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        operation.whenComplete((unloaded, throwable) -> {
            if (throwable == null) {
                result.complete(Boolean.TRUE.equals(unloaded));
            } else {
                result.completeExceptionally(new IllegalStateException(
                        "Failed to unload world \"" + worldName + "\" through the selected world lifecycle backend.",
                        unwrap(throwable)
                ));
            }
        });
        return result;
    }

    private CompletableFuture<Void> closeServerLevelAsync(World world, Object serverLevel) {
        Method closeMethod;
        try {
            closeMethod = RuntimeResolution.resolveMethod(
                    serverLevel.getClass(),
                    "close",
                    method -> method.getParameterCount() == 0
            );
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(unwrap(e));
        }
        if (closeMethod == null) {
            return CompletableFuture.completedFuture(null);
        }

        Runnable closeTask = () -> {
            try {
                closeMethod.invoke(serverLevel);
            } catch (Throwable e) {
                throw new RuntimeException(unwrap(e));
            }
        };
        return runGlobalAsync(closeTask).orTimeout(90L, TimeUnit.SECONDS);
    }

    private CompletableFuture<Void> drainChunkTasksAsync(World world, Object serverLevel) {
        Method schedulerMethod;
        try {
            schedulerMethod = RuntimeResolution.resolveMethod(
                    serverLevel.getClass(),
                    "moonrise$getChunkTaskScheduler",
                    method -> method.getParameterCount() == 0
            );
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(unwrap(e));
        }
        if (schedulerMethod == null) {
            return CompletableFuture.completedFuture(null);
        }

        return execution.runAsync(() -> {
            try {
                Object scheduler = schedulerMethod.invoke(serverLevel);
                Method haltMethod = RuntimeResolution.resolveMethod(
                        scheduler.getClass(),
                        "halt",
                        method -> {
                            Class<?>[] parameters = method.getParameterTypes();
                            return parameters.length == 2
                                    && boolean.class.equals(parameters[0])
                                    && long.class.equals(parameters[1]);
                        }
                );
                if (haltMethod == null) {
                    return;
                }
                Object halted = haltMethod.invoke(
                        scheduler,
                        true,
                        TimeUnit.SECONDS.toNanos(90L));
                if (halted instanceof Boolean complete && !complete) {
                    throw new IllegalStateException(
                            "Chunk scheduler drain timed out for world \"" + world.getName() + "\".");
                }
            } catch (Throwable e) {
                throw new RuntimeException(unwrap(e));
            }
        }).orTimeout(90L, TimeUnit.SECONDS);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void removeWorldFromCraftServerMap(World world) throws ReflectiveOperationException {
        Object bukkitServer = Bukkit.getServer();
        if (bukkitServer == null) {
            return;
        }

        Field worldsField = RuntimeResolution.resolveField(bukkitServer.getClass(), "worlds");
        Object rawWorlds = worldsField.get(bukkitServer);
        if (rawWorlds instanceof Map map) {
            boolean removed = map.values().removeIf(candidate -> candidate == world);
            if (!removed) {
                throw new IllegalStateException(
                        "CraftServer world registry did not contain \"" + world.getName() + "\".");
            }
        }
    }

    private CompletableFuture<Void> detachServerLevelAsync(
            RuntimeCapabilities capabilities,
            Object serverLevel,
            World world
    ) {
        Runnable detachTask = () -> {
            try {
                capabilities.removeLevelMethod().invoke(capabilities.minecraftServer(), serverLevel);
                removeWorldFromCraftServerMap(world);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        };

        if (!execution.regionized() || isGlobalTickThread()) {
            try {
                detachTask.run();
                return CompletableFuture.completedFuture(null);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(unwrap(e));
            }
        }

        CompletableFuture<Void> detachFuture = runGlobalAsync(detachTask);
        return detachFuture.orTimeout(15L, TimeUnit.SECONDS);
    }

    private CompletableFuture<Void> runGlobalAsync(Runnable task) {
        return execution.runGlobal(task);
    }

    static boolean isGlobalTickThread() {
        Object server = Bukkit.getServer();
        if (server == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(server.getClass().getMethod("isGlobalTickThread").invoke(server));
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }
}
