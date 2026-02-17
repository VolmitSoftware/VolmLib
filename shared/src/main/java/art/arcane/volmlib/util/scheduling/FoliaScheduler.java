package art.arcane.volmlib.util.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reflection-backed helper for Folia scheduling APIs with safe fallbacks for non-Folia runtimes.
 */
public final class FoliaScheduler {
    private static final long TICK_MS = 50L;

    private static final Method SERVER_GET_GLOBAL_REGION_SCHEDULER = resolveServerMethod("getGlobalRegionScheduler");
    private static final Method SERVER_GET_REGION_SCHEDULER = resolveServerMethod("getRegionScheduler");
    private static final Method SERVER_GET_ASYNC_SCHEDULER = resolveServerMethod("getAsyncScheduler");
    private static final Method SERVER_IS_TICK_THREAD = resolveServerMethod("isTickThread");
    private static final Method SERVER_IS_GLOBAL_TICK_THREAD = resolveServerMethod("isGlobalTickThread");
    private static final Method SERVER_IS_OWNED_LOCATION_REGION = resolveServerMethod("isOwnedByCurrentRegion", Location.class);
    private static final Method SERVER_IS_OWNED_ENTITY_REGION = resolveServerMethod("isOwnedByCurrentRegion", Entity.class);
    private static final Method SERVER_IS_OWNED_WORLD_CHUNK_REGION = resolveServerMethod("isOwnedByCurrentRegion", World.class, int.class, int.class);

    private static final Method BUKKIT_GET_GLOBAL_REGION_SCHEDULER = resolveBukkitMethod("getGlobalRegionScheduler");
    private static final Method BUKKIT_GET_REGION_SCHEDULER = resolveBukkitMethod("getRegionScheduler");
    private static final Method BUKKIT_GET_ASYNC_SCHEDULER = resolveBukkitMethod("getAsyncScheduler");

    private static final Method BUKKIT_IS_TICK_THREAD = resolveBukkitMethod("isTickThread");
    private static final Method BUKKIT_IS_GLOBAL_TICK_THREAD = resolveBukkitMethod("isGlobalTickThread");
    private static final Method BUKKIT_IS_OWNED_LOCATION_REGION = resolveBukkitMethod("isOwnedByCurrentRegion", Location.class);
    private static final Method BUKKIT_IS_OWNED_ENTITY_REGION = resolveBukkitMethod("isOwnedByCurrentRegion", Entity.class);
    private static final Method BUKKIT_IS_OWNED_WORLD_CHUNK_REGION = resolveBukkitMethod("isOwnedByCurrentRegion", World.class, int.class, int.class);

    private static final Method ENTITY_GET_SCHEDULER = resolveEntityMethod("getScheduler");

    private FoliaScheduler() {
    }

    public static boolean isFolia(Server server) {
        return getGlobalRegionScheduler(server) != null
                || getRegionScheduler(server) != null
                || getAsyncScheduler(server) != null;
    }

    public static boolean isFolia(Plugin plugin) {
        Server server = plugin == null ? Bukkit.getServer() : plugin.getServer();
        return isFolia(server);
    }

    public static boolean isFoliaThreading(Server server) {
        return getGlobalRegionScheduler(server) != null || getRegionScheduler(server) != null;
    }

    public static boolean isPrimaryThread() {
        Server server = Bukkit.getServer();
        Boolean serverTickThread = invokeBooleanNoThrow(SERVER_IS_TICK_THREAD, server);
        if (serverTickThread == null) {
            serverTickThread = invokeBooleanNoThrow(server, "isTickThread", new Class<?>[0]);
        }
        if (serverTickThread != null) {
            return serverTickThread;
        }

        Boolean tickThread = invokeBooleanNoThrow(BUKKIT_IS_TICK_THREAD, null);
        if (tickThread != null) {
            return tickThread;
        }

        boolean bukkitPrimaryThread = Bukkit.isPrimaryThread();
        if (bukkitPrimaryThread) {
            return true;
        }

        Boolean serverGlobalTickThread = invokeBooleanNoThrow(SERVER_IS_GLOBAL_TICK_THREAD, server);
        if (serverGlobalTickThread == null) {
            serverGlobalTickThread = invokeBooleanNoThrow(server, "isGlobalTickThread", new Class<?>[0]);
        }
        if (serverGlobalTickThread != null) {
            return serverGlobalTickThread;
        }

        Boolean globalTickThread = invokeBooleanNoThrow(BUKKIT_IS_GLOBAL_TICK_THREAD, null);
        if (globalTickThread != null) {
            return globalTickThread;
        }

        return bukkitPrimaryThread;
    }

    public static boolean isOwnedByCurrentRegion(Entity entity) {
        if (entity == null) {
            return false;
        }

        Boolean bukkitOwned = invokeBooleanNoThrow(BUKKIT_IS_OWNED_ENTITY_REGION, null, entity);
        if (bukkitOwned != null) {
            return bukkitOwned;
        }

        Server server = Bukkit.getServer();
        Boolean serverOwned = invokeBooleanNoThrow(SERVER_IS_OWNED_ENTITY_REGION, server, entity);
        if (serverOwned != null) {
            return serverOwned;
        }

        if (!isFolia(server)) {
            return isPrimaryThread();
        }

        Location location = safeEntityLocation(entity);
        return location != null && isOwnedByCurrentRegion(location);
    }

    public static boolean isOwnedByCurrentRegion(Location location) {
        if (location == null) {
            return false;
        }

        Boolean bukkitOwned = invokeBooleanNoThrow(BUKKIT_IS_OWNED_LOCATION_REGION, null, location);
        if (bukkitOwned != null) {
            return bukkitOwned;
        }

        Server server = Bukkit.getServer();
        Boolean serverOwned = invokeBooleanNoThrow(SERVER_IS_OWNED_LOCATION_REGION, server, location);
        if (serverOwned != null) {
            return serverOwned;
        }

        World world = location.getWorld();
        if (world != null) {
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            Boolean chunkOwned = ownershipByChunk(world, chunkX, chunkZ, server);
            if (chunkOwned != null) {
                return chunkOwned;
            }
        }

        if (!isFolia(server)) {
            return isPrimaryThread();
        }

        return false;
    }

    public static boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return false;
        }

        Server server = Bukkit.getServer();
        Boolean owned = ownershipByChunk(world, chunkX, chunkZ, server);
        if (owned != null) {
            return owned;
        }

        if (!isFolia(server)) {
            return isPrimaryThread();
        }

        return false;
    }

    public static boolean runGlobal(Plugin plugin, Runnable runnable) {
        return runGlobal(plugin, runnable, 0L);
    }

    public static boolean runGlobal(Plugin plugin, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || runnable == null) {
            return false;
        }

        Object scheduler = getGlobalRegionScheduler(plugin);
        if (scheduler == null) {
            return false;
        }

        long safeDelay = Math.max(0L, delayTicks);
        Consumer<Object> consumer = task -> runnable.run();
        if (safeDelay <= 0L) {
            if (invokeVoidNoThrow(scheduler, "execute", new Class<?>[]{Plugin.class, Runnable.class}, plugin, runnable)) {
                return true;
            }

            return invokeVoidNoThrow(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, consumer);
        }

        return invokeVoidNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, consumer, safeDelay);
    }

    public static boolean runRegion(Plugin plugin, Location location, Runnable runnable) {
        return runRegion(plugin, location, runnable, 0L);
    }

    public static boolean runRegion(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || location == null || runnable == null) {
            return false;
        }

        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return runRegion(plugin, world, chunkX, chunkZ, location, runnable, delayTicks);
    }

    public static boolean runRegion(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable) {
        return runRegion(plugin, world, chunkX, chunkZ, null, runnable, 0L);
    }

    public static boolean runRegion(Plugin plugin, World world, int chunkX, int chunkZ, Runnable runnable, long delayTicks) {
        return runRegion(plugin, world, chunkX, chunkZ, null, runnable, delayTicks);
    }

    public static boolean runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        return runEntity(plugin, entity, runnable, 0L);
    }

    public static boolean runEntity(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || entity == null || runnable == null) {
            return false;
        }

        long safeDelay = Math.max(0L, delayTicks);
        if (safeDelay <= 0L && isOwnedByCurrentRegion(entity)) {
            runnable.run();
            return true;
        }

        Object scheduler = ENTITY_GET_SCHEDULER == null ? null : invokeNoThrow(ENTITY_GET_SCHEDULER, entity);
        if (scheduler == null) {
            return false;
        }

        Runnable retired = () -> {
        };
        Consumer<Object> consumer = task -> runnable.run();

        if (safeDelay <= 0L) {
            Object executed = invokeNoThrow(
                    scheduler,
                    "execute",
                    new Class<?>[]{Plugin.class, Runnable.class, Runnable.class, long.class},
                    plugin,
                    runnable,
                    retired,
                    0L
            );

            if (executed instanceof Boolean done) {
                return done;
            }

            if (invokeVoidNoThrow(
                    scheduler,
                    "run",
                    new Class<?>[]{Plugin.class, Consumer.class, Runnable.class},
                    plugin,
                    consumer,
                    retired
            )) {
                return true;
            }

            return invokeVoidNoThrow(
                    scheduler,
                    "runDelayed",
                    new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class},
                    plugin,
                    consumer,
                    retired,
                    1L
            );
        }

        if (invokeVoidNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class},
                plugin,
                consumer,
                retired,
                safeDelay
        )) {
            return true;
        }

        Object executed = invokeNoThrow(
                scheduler,
                "execute",
                new Class<?>[]{Plugin.class, Runnable.class, Runnable.class, long.class},
                plugin,
                runnable,
                retired,
                safeDelay
        );

        return executed instanceof Boolean done && done;
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable) {
        return runAsync(plugin, runnable, 0L);
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || runnable == null) {
            return false;
        }

        Object scheduler = getAsyncScheduler(plugin);
        if (scheduler == null) {
            return false;
        }

        long safeDelay = Math.max(0L, delayTicks);
        Consumer<Object> consumer = task -> runnable.run();
        if (safeDelay <= 0L) {
            if (invokeVoidNoThrow(scheduler, "runNow", new Class<?>[]{Plugin.class, Consumer.class}, plugin, consumer)) {
                return true;
            }

            return invokeVoidNoThrow(scheduler, "runNow", new Class<?>[]{Plugin.class, Runnable.class}, plugin, runnable);
        }

        long delayMs = ticksToMilliseconds(safeDelay);
        if (invokeVoidNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class},
                plugin,
                consumer,
                delayMs,
                TimeUnit.MILLISECONDS
        )) {
            return true;
        }

        if (invokeVoidNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, Runnable.class, long.class, TimeUnit.class},
                plugin,
                runnable,
                delayMs,
                TimeUnit.MILLISECONDS
        )) {
            return true;
        }

        if (invokeVoidNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, consumer, safeDelay)) {
            return true;
        }

        return invokeVoidNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Runnable.class, long.class}, plugin, runnable, safeDelay);
    }

    public static void cancelTasks(Plugin plugin) {
        if (!isPluginActive(plugin)) {
            return;
        }

        Object globalScheduler = getGlobalRegionScheduler(plugin);
        if (globalScheduler != null) {
            invokeNoThrow(globalScheduler, "cancelTasks", new Class<?>[]{Plugin.class}, plugin);
        }

        Object asyncScheduler = getAsyncScheduler(plugin);
        if (asyncScheduler != null) {
            invokeNoThrow(asyncScheduler, "cancelTasks", new Class<?>[]{Plugin.class}, plugin);
        }
    }

    private static boolean runRegion(Plugin plugin, World world, int chunkX, int chunkZ, Location location, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || world == null || runnable == null) {
            return false;
        }

        Object scheduler = getRegionScheduler(plugin);
        if (scheduler == null) {
            return false;
        }

        long safeDelay = Math.max(0L, delayTicks);
        Consumer<Object> consumer = task -> runnable.run();
        if (safeDelay <= 0L) {
            if (location != null) {
                if (invokeVoidNoThrow(scheduler, "execute", new Class<?>[]{Plugin.class, Location.class, Runnable.class}, plugin, location, runnable)) {
                    return true;
                }

                if (invokeVoidNoThrow(scheduler, "run", new Class<?>[]{Plugin.class, Location.class, Consumer.class}, plugin, location, consumer)) {
                    return true;
                }
            }

            if (invokeVoidNoThrow(
                    scheduler,
                    "execute",
                    new Class<?>[]{Plugin.class, World.class, int.class, int.class, Runnable.class},
                    plugin,
                    world,
                    chunkX,
                    chunkZ,
                    runnable
            )) {
                return true;
            }

            return invokeVoidNoThrow(
                    scheduler,
                    "run",
                    new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class},
                    plugin,
                    world,
                    chunkX,
                    chunkZ,
                    consumer
            );
        }

        if (location != null && invokeVoidNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, Location.class, Consumer.class, long.class},
                plugin,
                location,
                consumer,
                safeDelay
        )) {
            return true;
        }

        return invokeVoidNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class, long.class},
                plugin,
                world,
                chunkX,
                chunkZ,
                consumer,
                safeDelay
        );
    }

    private static Object getGlobalRegionScheduler(Plugin plugin) {
        return getGlobalRegionScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getGlobalRegionScheduler(Server server) {
        if (server != null && SERVER_GET_GLOBAL_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_GLOBAL_REGION_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_GLOBAL_REGION_SCHEDULER != null) {
            return invokeNoThrow(BUKKIT_GET_GLOBAL_REGION_SCHEDULER, null);
        }

        return null;
    }

    private static Object getRegionScheduler(Plugin plugin) {
        return getRegionScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getRegionScheduler(Server server) {
        if (server != null && SERVER_GET_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_REGION_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_REGION_SCHEDULER != null) {
            return invokeNoThrow(BUKKIT_GET_REGION_SCHEDULER, null);
        }

        return null;
    }

    private static Object getAsyncScheduler(Plugin plugin) {
        return getAsyncScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getAsyncScheduler(Server server) {
        if (server != null && SERVER_GET_ASYNC_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_ASYNC_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_ASYNC_SCHEDULER != null) {
            return invokeNoThrow(BUKKIT_GET_ASYNC_SCHEDULER, null);
        }

        return null;
    }

    private static Method resolveServerMethod(String methodName) {
        return resolveServerMethod(methodName, new Class<?>[0]);
    }

    private static Method resolveServerMethod(String methodName, Class<?>... parameterTypes) {
        try {
            return Server.class.getMethod(methodName, parameterTypes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean ownershipByChunk(World world, int chunkX, int chunkZ, Server server) {
        Boolean bukkitOwned = invokeBooleanNoThrow(BUKKIT_IS_OWNED_WORLD_CHUNK_REGION, null, world, chunkX, chunkZ);
        if (bukkitOwned != null) {
            return bukkitOwned;
        }

        return invokeBooleanNoThrow(SERVER_IS_OWNED_WORLD_CHUNK_REGION, server, world, chunkX, chunkZ);
    }

    private static Method resolveBukkitMethod(String methodName, Class<?>... parameterTypes) {
        try {
            return Bukkit.class.getMethod(methodName, parameterTypes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveEntityMethod(String methodName) {
        try {
            return Entity.class.getMethod(methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static long ticksToMilliseconds(long ticks) {
        return Math.max(0L, ticks) * TICK_MS;
    }

    private static Object invokeNoThrow(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Boolean invokeBooleanNoThrow(Method method, Object target, Object... args) {
        Object value = invokeNoThrow(method, target, args);
        if (value instanceof Boolean bool) {
            return bool;
        }

        return null;
    }

    private static Boolean invokeBooleanNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object value = invokeNoThrow(target, methodName, parameterTypes, args);
        if (value instanceof Boolean bool) {
            return bool;
        }

        return null;
    }

    private static Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeVoidNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return false;
        }

        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            method.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Location safeEntityLocation(Entity entity) {
        try {
            return entity.getLocation();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isPluginActive(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }
}
