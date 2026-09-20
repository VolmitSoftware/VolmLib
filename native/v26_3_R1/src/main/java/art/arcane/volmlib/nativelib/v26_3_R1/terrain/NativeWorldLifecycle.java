package art.arcane.volmlib.nativelib.v26_3_R1.terrain;


import art.arcane.volmlib.nativelib.terrain.ServerShutdownBoundary;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicies;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeWorldLifecycle implements NativeWorldLifecycleFactory.Controller {
    private final NativeWorldLifecyclePolicy policy;
    private final String generatorClassName;
    private final AtomicBoolean injected = new AtomicBoolean();
    private volatile ResettableClassFileTransformer levelStorageAccessTransformer;
    private volatile ResettableClassFileTransformer serverLevelTransformer;
    private volatile ResettableClassFileTransformer pluginClassLoaderTransformer;
    private boolean pluginClassLoaderCloseDeferred;

    public NativeWorldLifecycle(NativeWorldLifecyclePolicy policy, String generatorClassName) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.generatorClassName = Objects.requireNonNull(generatorClassName, "generatorClassName");
    }

    public boolean injectBukkit() {
        synchronized (injected) {
            if (injected.get()) {
                return true;
            }
            try {
                policy.requireClassLoaderCloseDeferral();
                NativeWorldLifecyclePolicies.register(policy);
                Class<?> loaderCloseType = policy.getClass().getClassLoader().getClass()
                        .getMethod("close").getDeclaringClass();
                PluginClassLoaderInjectionListener loaderListener = new PluginClassLoaderInjectionListener();
                pluginClassLoaderTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .with(AgentBuilder.RedefinitionStrategy.Listener.ErrorEscalating.FAIL_FAST)
                        .with(loaderListener)
                        .type(ElementMatchers.is(loaderCloseType))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.withCustomMapping().bind(BridgeClassName.class, policy.classLoaderLifecycleBridgeName())
                                        .to(PluginClassLoaderCloseAdvice.class)
                                        .on(ElementMatchers.named("close")
                                                .and(ElementMatchers.takesArguments(0))
                                                .and(ElementMatchers.returns(void.class)))))
                        .installOn(policy.instrumentation());
                loaderListener.requireInstalled();
                levelStorageAccessTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .type(ElementMatchers.is(LevelStorageSource.LevelStorageAccess.class))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.withCustomMapping()
                                        .bind(PluginName.class, policy.pluginName())
                                        .bind(PolicyClassName.class, NativeWorldLifecyclePolicies.class.getName())
                                        .to(LevelStorageAccessAdvice.class).on(ElementMatchers.isConstructor()
                                        .and(ElementMatchers.takesArguments(4))
                                        .and(ElementMatchers.takesArgument(0, LevelStorageSource.class))
                                        .and(ElementMatchers.takesArgument(1, String.class))
                                        .and(ElementMatchers.takesArgument(2, Path.class))
                                        .and(ElementMatchers.takesArgument(3, ResourceKey.class)))))
                        .installOn(policy.instrumentation());
                serverLevelTransformer = new AgentBuilder.Default()
                        .disableClassFormatChanges()
                        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                        .type(ElementMatchers.is(ServerLevel.class))
                        .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(Advice.withCustomMapping()
                                        .bind(PluginName.class, policy.pluginName())
                                        .bind(PolicyClassName.class, NativeWorldLifecyclePolicies.class.getName())
                                        .to(ServerLevelAdvice.class).on(ElementMatchers.isConstructor()
                                        .and(ElementMatchers.takesArgument(0, MinecraftServer.class))
                                        .and(ElementMatchers.takesArgument(5, LevelStem.class)))))
                        .installOn(policy.instrumentation());
                NativeGenerationHooks.install(ClassReloadingStrategy.of(policy.instrumentation()), generatorClassName);

                injected.set(true);
                return true;
            } catch (Throwable e) {
                NativeWorldLifecyclePolicies.unregister(policy);
                policy.reportFailure("Failed to inject Bukkit", e);
                ResettableClassFileTransformer partialServerLevel = serverLevelTransformer;
                ResettableClassFileTransformer partialStorageAccess = levelStorageAccessTransformer;
                ResettableClassFileTransformer partialPluginClassLoader = pluginClassLoaderTransformer;
                serverLevelTransformer = null;
                levelStorageAccessTransformer = null;
                pluginClassLoaderTransformer = null;
                for (ResettableClassFileTransformer partial : new ResettableClassFileTransformer[]{
                        partialServerLevel,
                        partialStorageAccess,
                        partialPluginClassLoader
                }) {
                    if (partial == null) {
                        continue;
                    }
                    try {
                        partial.reset(policy.instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                    } catch (Throwable cleanupFailure) {
                        policy.reportFailure("Failed to remove partial Bukkit lifecycle injection", cleanupFailure);
                    }
                }
                return false;
            }
        }
    }

    public void ensureServerLevelInjection() {
        if (!injected.get()) {
            throw new IllegalStateException("World lifecycle injection is unavailable. Fix the Java Agent or Code Injection startup failure and restart before creating worlds.");
        }
        try {
            policy.instrumentation().retransformClasses(
                    LevelStorageSource.LevelStorageAccess.class,
                    ServerLevel.class
            );
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to re-apply Bukkit world lifecycle injection. World creation was stopped before loading the world.", e);
        }
    }

    public void uninjectBukkit() {
        synchronized (injected) {
            ResettableClassFileTransformer activeServerLevel = serverLevelTransformer;
            ResettableClassFileTransformer activeStorageAccess = levelStorageAccessTransformer;
            serverLevelTransformer = null;
            levelStorageAccessTransformer = null;
            injected.set(false);
            NativeWorldLifecyclePolicies.unregister(policy);
            for (ResettableClassFileTransformer transformer : new ResettableClassFileTransformer[]{
                    activeServerLevel,
                    activeStorageAccess
            }) {
                if (transformer == null) {
                    continue;
                }
                try {
                    transformer.reset(policy.instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                } catch (Throwable e) {
                    policy.reportFailure("Failed to remove Bukkit world lifecycle injection", e);
                }
            }
        }
    }

    public boolean isServerStopping() {
        return ((CraftServer) Bukkit.getServer()).getServer().hasStopped();
    }

    public ServerShutdownBoundary createServerShutdownBoundary() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        return new ServerShutdownBoundary(
                () -> server.hasFullyShutdown,
                server.getRunningThread()
        );
    }

    public void deferPluginClassLoaderClose() {
        synchronized (injected) {
            if (pluginClassLoaderTransformer == null || pluginClassLoaderCloseDeferred) {
                return;
            }
            policy.retainClassLoader(policy.getClass().getClassLoader());
            pluginClassLoaderCloseDeferred = true;
        }
    }

    public void releasePluginClassLoaderClose() {
        ResettableClassFileTransformer transformer;
        boolean releaseLoader;
        synchronized (injected) {
            transformer = pluginClassLoaderTransformer;
            pluginClassLoaderTransformer = null;
            releaseLoader = pluginClassLoaderCloseDeferred;
            pluginClassLoaderCloseDeferred = false;
        }
        try {
            if (transformer != null
                    && !transformer.reset(policy.instrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)) {
                throw new IllegalStateException("Failed to remove Plugin class loader lifecycle injection");
            }
        } finally {
            if (releaseLoader) {
                policy.releaseClassLoader(policy.getClass().getClassLoader());
            }
        }
    }

    private static final class PluginClassLoaderInjectionListener extends AgentBuilder.Listener.Adapter {
        private volatile boolean transformed;
        private volatile Throwable failure;

        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                     boolean loaded, DynamicType dynamicType) {
            transformed = true;
        }

        @Override
        public void onError(String typeName, ClassLoader loader, JavaModule module,
                            boolean loaded, Throwable throwable) {
            failure = throwable;
        }

        private void requireInstalled() {
            if (!transformed || failure != null) {
                throw new IllegalStateException("Plugin class loader lifecycle injection failed", failure);
            }
        }
    }

    private static class PluginClassLoaderCloseAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        static boolean enter(@Advice.This ClassLoader loader, @BridgeClassName String bridgeClassName) throws ReflectiveOperationException {
            Class<?> installer = Class.forName(
                    bridgeClassName, true, ClassLoader.getSystemClassLoader());
            return Boolean.TRUE.equals(installer.getMethod("deferClassLoaderClose", ClassLoader.class)
                    .invoke(null, loader));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface PluginName {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface PolicyClassName {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface BridgeClassName {
    }

    private static class LevelStorageAccessAdvice {
        @Advice.OnMethodEnter
        static void enter(
                @Advice.Argument(1) String levelId,
                @Advice.Argument(value = 3, readOnly = false) ResourceKey<LevelStem> dimensionType,
                @PluginName String pluginName,
                @PolicyClassName String policyClassName
        ) {
            if (levelId == null || levelId.isBlank()) {
                return;
            }
            Class<?> policyType;
            Object policy;
            try {
                Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin == null) {
                    return;
                }
                policyType = Class.forName(policyClassName, true, plugin.getClass().getClassLoader());
                policy = policyType.getMethod("policy", String.class).invoke(null, pluginName);
                if (policy == null) {
                    return;
                }
                policyType = policy.getClass();
            } catch (Throwable failure) {
                throw new RuntimeException("Failed to resolve managed world lifecycle policy",
                        failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
            }
            try {
                Object definition = policyType.getMethod("stagedWorld", String.class, ChunkGenerator.class, boolean.class)
                        .invoke(policy, levelId, null, false);
                if (definition == null) {
                    return;
                }
                String identity = (String) definition.getClass().getMethod("identity").invoke(definition);
                dimensionType = ResourceKey.create(Registries.LEVEL_STEM, Identifier.parse(identity));
            } catch (Throwable failure) {
                throw new RuntimeException("Failed to bind the staged world storage identity",
                        failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
            }
        }
    }

    private static class ServerLevelAdvice {
        @Advice.OnMethodEnter
        static void enter(
                @Advice.Argument(0) MinecraftServer server,
                @Advice.Argument(value = 4, readOnly = false) ResourceKey<Level> dimensionKey,
                @Advice.Argument(value = 5, readOnly = false) LevelStem levelStem,
                @Advice.AllArguments Object[] constructorArguments,
                @PluginName String pluginName,
                @PolicyClassName String policyClassName
        ) {
            if (dimensionKey == null) {
                return;
            }
            String levelId = dimensionKey.identifier().getPath();
            if (levelId == null || levelId.isBlank()) {
                return;
            }
            Class<?> policyType;
            Object policy;
            try {
                Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin == null) {
                    return;
                }
                policyType = Class.forName(policyClassName, true, plugin.getClass().getClassLoader());
                policy = policyType.getMethod("policy", String.class).invoke(null, pluginName);
                if (policy == null) {
                    return;
                }
                policyType = policy.getClass();
            } catch (Throwable failure) {
                throw new RuntimeException("Failed to resolve managed world lifecycle policy",
                        failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
            }
            try {
                ChunkGenerator constructorGenerator = null;
                for (Object argument : constructorArguments) {
                    if (argument instanceof ChunkGenerator candidate) {
                        constructorGenerator = candidate;
                        break;
                    }
                }
                Object definition = policyType.getMethod("stagedWorld", String.class, ChunkGenerator.class, boolean.class)
                        .invoke(policy, levelId, constructorGenerator, true);
                if (definition == null) {
                    return;
                }
                String identity = (String) definition.getClass().getMethod("identity").invoke(definition);
                String dimensionTypeKey = (String) definition.getClass().getMethod("dimensionTypeKey").invoke(definition);
                FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(Optional.empty(),
                        server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID), List.of());
                settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.AIR));
                settings.updateLayers();
                levelStem = new LevelStem(server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                        .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(dimensionTypeKey))),
                        new FlatLevelSource(settings));
                dimensionKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(identity));
            } catch (Throwable failure) {
                throw new RuntimeException("Failed to replace the managed world level stem",
                        failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure);
            }
        }
    }
}
