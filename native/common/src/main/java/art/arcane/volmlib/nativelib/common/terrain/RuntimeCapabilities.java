package art.arcane.volmlib.nativelib.common.terrain;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

final class RuntimeCapabilities {
    public enum PaperLikeFlavor {
        CURRENT_INFO_AND_DATA,
        LEGACY_STORAGE_ACCESS,
        UNSUPPORTED
    }

    private final Object bukkitServer;
    private final Object minecraftServer;
    private final Method createLevelMethod;
    private final PaperLikeFlavor paperLikeFlavor;
    private final Class<?> paperWorldLoaderClass;
    private final Method paperWorldDataMethod;
    private final Constructor<?> worldLoadingInfoConstructor;
    private final Constructor<?> worldLoadingInfoAndDataConstructor;
    private final Method createNewWorldDataMethod;
    private final Method levelStorageAccessMethod;
    private final Field worldLoaderContextField;
    private final Method serverRegistryAccessMethod;
    private final Field settingsField;
    private final Field optionsField;
    private final Method isDemoMethod;
    private final Method unloadWorldAsyncMethod;
    private final Method removeLevelMethod;
    private final String paperLikeResolution;

    RuntimeCapabilities(
            Object bukkitServer,
            Object minecraftServer,
            Method createLevelMethod,
            PaperLikeFlavor paperLikeFlavor,
            Class<?> paperWorldLoaderClass,
            Method paperWorldDataMethod,
            Constructor<?> worldLoadingInfoConstructor,
            Constructor<?> worldLoadingInfoAndDataConstructor,
            Method createNewWorldDataMethod,
            Method levelStorageAccessMethod,
            Field worldLoaderContextField,
            Method serverRegistryAccessMethod,
            Field settingsField,
            Field optionsField,
            Method isDemoMethod,
            Method unloadWorldAsyncMethod,
            Method removeLevelMethod,
            String paperLikeResolution
    ) {
        this.bukkitServer = bukkitServer;
        this.minecraftServer = minecraftServer;
        this.createLevelMethod = createLevelMethod;
        this.paperLikeFlavor = paperLikeFlavor;
        this.paperWorldLoaderClass = paperWorldLoaderClass;
        this.paperWorldDataMethod = paperWorldDataMethod;
        this.worldLoadingInfoConstructor = worldLoadingInfoConstructor;
        this.worldLoadingInfoAndDataConstructor = worldLoadingInfoAndDataConstructor;
        this.createNewWorldDataMethod = createNewWorldDataMethod;
        this.levelStorageAccessMethod = levelStorageAccessMethod;
        this.worldLoaderContextField = worldLoaderContextField;
        this.serverRegistryAccessMethod = serverRegistryAccessMethod;
        this.settingsField = settingsField;
        this.optionsField = optionsField;
        this.isDemoMethod = isDemoMethod;
        this.unloadWorldAsyncMethod = unloadWorldAsyncMethod;
        this.removeLevelMethod = removeLevelMethod;
        this.paperLikeResolution = paperLikeResolution;
    }

    public static RuntimeCapabilities probe() {
        Server server = Bukkit.getServer();
        Object bukkitServer = server;
        Object minecraftServer = null;
        Method createLevelMethod = null;
        PaperLikeFlavor paperLikeFlavor = PaperLikeFlavor.UNSUPPORTED;
        Class<?> paperWorldLoaderClass = null;
        Method paperWorldDataMethod = null;
        Constructor<?> worldLoadingInfoConstructor = null;
        Constructor<?> worldLoadingInfoAndDataConstructor = null;
        Method createNewWorldDataMethod = null;
        Method levelStorageAccessMethod = null;
        Field worldLoaderContextField = null;
        Method serverRegistryAccessMethod = null;
        Field settingsField = null;
        Field optionsField = null;
        Method isDemoMethod = null;
        Method removeLevelMethod = null;
        String paperLikeResolution = "inactive";

        try {
            if (bukkitServer != null) {
                Method getServerMethod = RuntimeResolution.resolveMethod(bukkitServer.getClass(), "getServer", method -> method.getParameterCount() == 0);
                if (getServerMethod != null) {
                    minecraftServer = getServerMethod.invoke(bukkitServer);
                }
            }

            if (minecraftServer != null) {
                Class<?> minecraftServerClass = Class.forName("net.minecraft.server.MinecraftServer");
                if (!minecraftServerClass.isInstance(minecraftServer)) {
                    throw new IllegalStateException("resolved server is not a MinecraftServer: " + minecraftServer.getClass().getName());
                }

                createLevelMethod = RuntimeResolution.resolveCreateLevelMethod(minecraftServer.getClass());
                removeLevelMethod = RuntimeResolution.resolveMethod(minecraftServer.getClass(), "removeLevel", method -> {
                    Class<?>[] params = method.getParameterTypes();
                    return params.length == 1 && "ServerLevel".equals(params[0].getSimpleName());
                });
                worldLoaderContextField = RuntimeResolution.resolveField(minecraftServer.getClass(), "worldLoaderContext");
                serverRegistryAccessMethod = RuntimeResolution.resolveServerRegistryAccessMethod(minecraftServer.getClass());
                settingsField = RuntimeResolution.resolveField(minecraftServer.getClass(), "settings");
                optionsField = RuntimeResolution.resolveField(minecraftServer.getClass(), "options");
                isDemoMethod = RuntimeResolution.resolveMethod(minecraftServer.getClass(), "isDemo", method -> method.getParameterCount() == 0 && boolean.class.equals(method.getReturnType()));

                Class<?> mainClass = Class.forName("net.minecraft.server.Main");
                createNewWorldDataMethod = RuntimeResolution.resolveCreateNewWorldDataMethod(mainClass);

                Class<?> paperLoaderCandidate = Class.forName("io.papermc.paper.world.PaperWorldLoader");
                paperWorldLoaderClass = paperLoaderCandidate;
                paperWorldDataMethod = RuntimeResolution.resolvePaperWorldDataMethod(paperLoaderCandidate);
                Class<?> worldLoadingInfoClass = Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfo");
                worldLoadingInfoConstructor = RuntimeResolution.resolveWorldLoadingInfoConstructor(worldLoadingInfoClass);

                if (createLevelMethod.getParameterCount() == 3) {
                    Class<?> worldLoadingInfoAndDataClass = Class.forName("io.papermc.paper.world.PaperWorldLoader$WorldLoadingInfoAndData");
                    worldLoadingInfoAndDataConstructor = RuntimeResolution.resolveWorldLoadingInfoAndDataConstructor(worldLoadingInfoAndDataClass);
                    paperLikeFlavor = PaperLikeFlavor.CURRENT_INFO_AND_DATA;
                } else {
                    Class<?> levelStorageSourceClass = Class.forName("net.minecraft.world.level.storage.LevelStorageSource");
                    levelStorageAccessMethod = RuntimeResolution.resolveLevelStorageAccessMethod(levelStorageSourceClass);
                    paperLikeFlavor = PaperLikeFlavor.LEGACY_STORAGE_ACCESS;
                }

                paperLikeResolution = "available(flavor=" + paperLikeFlavor.name().toLowerCase(Locale.ROOT)
                        + ", createLevel=" + createLevelMethod.toGenericString() + ")";
            }
        } catch (Throwable e) {
            paperLikeResolution = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            createLevelMethod = null;
            paperLikeFlavor = PaperLikeFlavor.UNSUPPORTED;
            paperWorldLoaderClass = null;
            paperWorldDataMethod = null;
            worldLoadingInfoConstructor = null;
            worldLoadingInfoAndDataConstructor = null;
            createNewWorldDataMethod = null;
            levelStorageAccessMethod = null;
            worldLoaderContextField = null;
            serverRegistryAccessMethod = null;
            settingsField = null;
            optionsField = null;
            isDemoMethod = null;
            removeLevelMethod = null;
        }

        Object resolvedServer = bukkitServer;
        Method unloadWorldAsyncMethod = resolvedServer == null ? null : RuntimeResolution.resolveMethod(resolvedServer.getClass(), "unloadWorldAsync", method -> {
                    Class<?>[] params = method.getParameterTypes();
                    return params.length == 3
                            && World.class.equals(params[0])
                            && boolean.class.equals(params[1])
                            && "Consumer".equals(params[2].getSimpleName());
                });

        return new RuntimeCapabilities(
                bukkitServer,
                minecraftServer,
                createLevelMethod,
                paperLikeFlavor,
                paperWorldLoaderClass,
                paperWorldDataMethod,
                worldLoadingInfoConstructor,
                worldLoadingInfoAndDataConstructor,
                createNewWorldDataMethod,
                levelStorageAccessMethod,
                worldLoaderContextField,
                serverRegistryAccessMethod,
                settingsField,
                optionsField,
                isDemoMethod,
                unloadWorldAsyncMethod,
                removeLevelMethod,
                paperLikeResolution
        );
    }

    public Object bukkitServer() {
        return bukkitServer;
    }

    public Object minecraftServer() {
        return minecraftServer;
    }

    public Method createLevelMethod() {
        return createLevelMethod;
    }

    public PaperLikeFlavor paperLikeFlavor() {
        return paperLikeFlavor;
    }

    public Class<?> paperWorldLoaderClass() {
        return paperWorldLoaderClass;
    }

    public Method paperWorldDataMethod() {
        return paperWorldDataMethod;
    }

    public Constructor<?> worldLoadingInfoConstructor() {
        return worldLoadingInfoConstructor;
    }

    public Constructor<?> worldLoadingInfoAndDataConstructor() {
        return worldLoadingInfoAndDataConstructor;
    }

    public Method createNewWorldDataMethod() {
        return createNewWorldDataMethod;
    }

    public Method levelStorageAccessMethod() {
        return levelStorageAccessMethod;
    }

    public Field worldLoaderContextField() {
        return worldLoaderContextField;
    }

    public Method serverRegistryAccessMethod() {
        return serverRegistryAccessMethod;
    }

    public Field settingsField() {
        return settingsField;
    }

    public Field optionsField() {
        return optionsField;
    }

    public Method isDemoMethod() {
        return isDemoMethod;
    }

    public Method unloadWorldAsyncMethod() {
        return unloadWorldAsyncMethod;
    }

    public Method removeLevelMethod() {
        return removeLevelMethod;
    }

    public boolean hasPaperLikeRuntime() {
        return minecraftServer != null
                && createLevelMethod != null
                && serverRegistryAccessMethod != null
                && paperLikeFlavor != PaperLikeFlavor.UNSUPPORTED;
    }

    public String paperLikeResolution() {
        return paperLikeResolution;
    }

}
