package art.arcane.volmlib.util.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reflection-backed helper for Folia scheduling APIs with safe fallbacks for non-Folia runtimes.
 */
public final class FoliaScheduler {
    private static final long TICK_MS = 50L;
    private static final Class<?> REGIONIZED_SERVER_CLASS = resolveClass("io.papermc.paper.threadedregions.RegionizedServer");
    private static volatile boolean forcedFoliaThreading;

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

    private static final Object METHOD_MISS = new Object();
    private static final ConcurrentHashMap<MethodKey, Object> METHOD_CACHE = new ConcurrentHashMap<>(64);

    private static volatile SchedulerHandle globalRegionSchedulerHandle;
    private static volatile SchedulerHandle regionSchedulerHandle;
    private static volatile SchedulerHandle asyncSchedulerHandle;

    private FoliaScheduler() {
    }

    public static boolean isFolia(Server server) {
        return isFoliaThreading(server);
    }

    public static boolean isFolia(Plugin plugin) {
        Server server = plugin == null ? Bukkit.getServer() : plugin.getServer();
        return isFolia(server);
    }

    public static boolean isFoliaThreading(Server server) {
        if (forcedFoliaThreading) {
            return true;
        }

        Server activeServer = server == null ? Bukkit.getServer() : server;
        if (activeServer == null || REGIONIZED_SERVER_CLASS == null) {
            return false;
        }

        return getGlobalRegionScheduler(activeServer) != null || getRegionScheduler(activeServer) != null;
    }

    public static boolean isRegionizedRuntime(Server server) {
        return isFoliaThreading(server);
    }

    public static void forceFoliaThreading(Server server) {
        forcedFoliaThreading = true;
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
        long safeDelay = Math.max(0L, delayTicks);
        if (scheduler != null) {
            Consumer<Object> consumer = task -> runnable.run();
            if (safeDelay <= 0L) {
                if (invokeScheduleNoThrow(scheduler, "execute", new Class<?>[]{Plugin.class, Runnable.class}, plugin, runnable) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(scheduler, "run", new Class<?>[]{Plugin.class, Consumer.class}, plugin, consumer) == ScheduleResult.SCHEDULED) {
                    return true;
                }
            } else if (invokeScheduleNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, consumer, safeDelay) == ScheduleResult.SCHEDULED) {
                return true;
            }
        }

        return !isFoliaThreading(plugin.getServer()) && runBukkitSync(plugin, runnable, safeDelay);
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
        return runEntity(plugin, entity, runnable, delayTicks, null);
    }

    public static boolean runEntity(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, Runnable retired) {
        if (!isPluginActive(plugin) || entity == null || runnable == null) {
            return false;
        }

        long safeDelay = Math.max(0L, delayTicks);
        if (safeDelay <= 0L && isOwnedByCurrentRegion(entity)) {
            if (retired != null && !isEntityActive(entity)) {
                retired.run();
                return true;
            }
            runnable.run();
            return true;
        }

        Object scheduler = ENTITY_GET_SCHEDULER == null ? null : invokeNoThrow(ENTITY_GET_SCHEDULER, entity);
        if (scheduler == null) {
            scheduler = invokeNoThrow(entity, "getScheduler", new Class<?>[0]);
        }

        Runnable fallbackTask = guardedEntityTask(entity, runnable, retired);
        if (scheduler == null) {
            return !isFoliaThreading(plugin.getServer()) && runBukkitSync(plugin, fallbackTask, safeDelay);
        }

        Runnable retireCallback = retired == null ? () -> {
        } : retired;
        Consumer<Object> consumer = task -> runnable.run();

        if (safeDelay <= 0L) {
            ScheduleResult executed = invokeScheduleNoThrow(
                    scheduler,
                    "execute",
                    new Class<?>[]{Plugin.class, Runnable.class, Runnable.class, long.class},
                    plugin,
                    runnable,
                    retireCallback,
                    0L
            );

            if (executed == ScheduleResult.SCHEDULED) {
                return true;
            }

            if (executed == ScheduleResult.REJECTED) {
                return notScheduled(retired);
            }

            ScheduleResult ran = invokeScheduleNoThrow(
                    scheduler,
                    "run",
                    new Class<?>[]{Plugin.class, Consumer.class, Runnable.class},
                    plugin,
                    consumer,
                    retireCallback
            );

            if (ran == ScheduleResult.SCHEDULED) {
                return true;
            }

            if (ran == ScheduleResult.REJECTED) {
                return notScheduled(retired);
            }

            ScheduleResult delayedOnce = invokeScheduleNoThrow(
                    scheduler,
                    "runDelayed",
                    new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class},
                    plugin,
                    consumer,
                    retireCallback,
                    1L
            );

            if (delayedOnce == ScheduleResult.SCHEDULED) {
                return true;
            }

            if (delayedOnce == ScheduleResult.REJECTED) {
                return notScheduled(retired);
            }

            return !isFoliaThreading(plugin.getServer()) && runBukkitSync(plugin, fallbackTask, safeDelay);
        }

        ScheduleResult delayed = invokeScheduleNoThrow(
                scheduler,
                "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class},
                plugin,
                consumer,
                retireCallback,
                safeDelay
        );

        if (delayed == ScheduleResult.SCHEDULED) {
            return true;
        }

        if (delayed == ScheduleResult.REJECTED) {
            return notScheduled(retired);
        }

        ScheduleResult executed = invokeScheduleNoThrow(
                scheduler,
                "execute",
                new Class<?>[]{Plugin.class, Runnable.class, Runnable.class, long.class},
                plugin,
                runnable,
                retireCallback,
                safeDelay
        );

        if (executed == ScheduleResult.SCHEDULED) {
            return true;
        }

        if (executed == ScheduleResult.REJECTED) {
            return notScheduled(retired);
        }

        return !isFoliaThreading(plugin.getServer()) && runBukkitSync(plugin, fallbackTask, safeDelay);
    }

    private static boolean notScheduled(Runnable retired) {
        if (retired != null) {
            retired.run();
        }

        return false;
    }

    private static Runnable guardedEntityTask(Entity entity, Runnable runnable, Runnable retired) {
        if (retired == null) {
            return runnable;
        }

        return () -> {
            if (isEntityActive(entity)) {
                runnable.run();
            } else {
                retired.run();
            }
        };
    }

    private static boolean isEntityActive(Entity entity) {
        try {
            return entity.isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable) {
        return runAsync(plugin, runnable, 0L);
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || runnable == null) {
            return false;
        }

        Object scheduler = getAsyncScheduler(plugin);
        long safeDelay = Math.max(0L, delayTicks);
        if (scheduler != null) {
            Consumer<Object> consumer = task -> runnable.run();
            if (safeDelay <= 0L) {
                if (invokeScheduleNoThrow(scheduler, "runNow", new Class<?>[]{Plugin.class, Consumer.class}, plugin, consumer) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(scheduler, "runNow", new Class<?>[]{Plugin.class, Runnable.class}, plugin, runnable) == ScheduleResult.SCHEDULED) {
                    return true;
                }
            } else {
                long delayMs = ticksToMilliseconds(safeDelay);
                if (invokeScheduleNoThrow(
                        scheduler,
                        "runDelayed",
                        new Class<?>[]{Plugin.class, Consumer.class, long.class, TimeUnit.class},
                        plugin,
                        consumer,
                        delayMs,
                        TimeUnit.MILLISECONDS
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(
                        scheduler,
                        "runDelayed",
                        new Class<?>[]{Plugin.class, Runnable.class, long.class, TimeUnit.class},
                        plugin,
                        runnable,
                        delayMs,
                        TimeUnit.MILLISECONDS
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, consumer, safeDelay) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(scheduler, "runDelayed", new Class<?>[]{Plugin.class, Runnable.class, long.class}, plugin, runnable, safeDelay) == ScheduleResult.SCHEDULED) {
                    return true;
                }
            }
        }

        return !isFoliaThreading(plugin.getServer()) && runBukkitAsync(plugin, runnable, safeDelay);
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

        if (!isFoliaThreading(plugin.getServer())) {
            cancelBukkitTasks(plugin);
        }
    }

    private static boolean runRegion(Plugin plugin, World world, int chunkX, int chunkZ, Location location, Runnable runnable, long delayTicks) {
        if (!isPluginActive(plugin) || world == null || runnable == null) {
            return false;
        }

        Object scheduler = getRegionScheduler(plugin);
        long safeDelay = Math.max(0L, delayTicks);
        if (scheduler != null) {
            Consumer<Object> consumer = task -> runnable.run();
            if (safeDelay <= 0L) {
                if (location != null) {
                    if (invokeScheduleNoThrow(scheduler, "execute", new Class<?>[]{Plugin.class, Location.class, Runnable.class}, plugin, location, runnable) == ScheduleResult.SCHEDULED) {
                        return true;
                    }

                    if (invokeScheduleNoThrow(scheduler, "run", new Class<?>[]{Plugin.class, Location.class, Consumer.class}, plugin, location, consumer) == ScheduleResult.SCHEDULED) {
                        return true;
                    }
                }

                if (invokeScheduleNoThrow(
                        scheduler,
                        "execute",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Runnable.class},
                        plugin,
                        world,
                        chunkX,
                        chunkZ,
                        runnable
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(
                        scheduler,
                        "run",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class},
                        plugin,
                        world,
                        chunkX,
                        chunkZ,
                        consumer
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }
            } else {
                if (location != null && invokeScheduleNoThrow(
                        scheduler,
                        "runDelayed",
                        new Class<?>[]{Plugin.class, Location.class, Consumer.class, long.class},
                        plugin,
                        location,
                        consumer,
                        safeDelay
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }

                if (invokeScheduleNoThrow(
                        scheduler,
                        "runDelayed",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class, long.class},
                        plugin,
                        world,
                        chunkX,
                        chunkZ,
                        consumer,
                        safeDelay
                ) == ScheduleResult.SCHEDULED) {
                    return true;
                }
            }
        }

        return !isFoliaThreading(plugin.getServer()) && runBukkitSync(plugin, runnable, safeDelay);
    }

    private static boolean runBukkitSync(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            BukkitTask task = delayTicks <= 0L
                    ? scheduler.runTask(plugin, runnable)
                    : scheduler.runTaskLater(plugin, runnable, delayTicks);
            return task != null;
        } catch (IllegalPluginAccessException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static boolean runBukkitAsync(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            BukkitScheduler scheduler = Bukkit.getScheduler();
            BukkitTask task = delayTicks <= 0L
                    ? scheduler.runTaskAsynchronously(plugin, runnable)
                    : scheduler.runTaskLaterAsynchronously(plugin, runnable, delayTicks);
            return task != null;
        } catch (IllegalPluginAccessException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static void cancelBukkitTasks(Plugin plugin) {
        Bukkit.getScheduler().cancelTasks(plugin);
    }

    private static Object getGlobalRegionScheduler(Plugin plugin) {
        return getGlobalRegionScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getGlobalRegionScheduler(Server server) {
        SchedulerHandle handle = globalRegionSchedulerHandle;
        if (handle != null && handle.server == server) {
            return handle.scheduler;
        }

        Object scheduler = resolveGlobalRegionScheduler(server);
        if (scheduler != null) {
            globalRegionSchedulerHandle = new SchedulerHandle(server, scheduler);
        }

        return scheduler;
    }

    private static Object resolveGlobalRegionScheduler(Server server) {
        if (server != null && SERVER_GET_GLOBAL_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_GLOBAL_REGION_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (server != null) {
            Object scheduler = invokeNoThrow(server, "getGlobalRegionScheduler", new Class<?>[0]);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_GLOBAL_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(BUKKIT_GET_GLOBAL_REGION_SCHEDULER, null);
            if (scheduler != null) {
                return scheduler;
            }
        }

        return invokeStaticNoThrow(Bukkit.class, "getGlobalRegionScheduler", new Class<?>[0]);
    }

    private static Object getRegionScheduler(Plugin plugin) {
        return getRegionScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getRegionScheduler(Server server) {
        SchedulerHandle handle = regionSchedulerHandle;
        if (handle != null && handle.server == server) {
            return handle.scheduler;
        }

        Object scheduler = resolveRegionScheduler(server);
        if (scheduler != null) {
            regionSchedulerHandle = new SchedulerHandle(server, scheduler);
        }

        return scheduler;
    }

    private static Object resolveRegionScheduler(Server server) {
        if (server != null && SERVER_GET_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_REGION_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (server != null) {
            Object scheduler = invokeNoThrow(server, "getRegionScheduler", new Class<?>[0]);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_REGION_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(BUKKIT_GET_REGION_SCHEDULER, null);
            if (scheduler != null) {
                return scheduler;
            }
        }

        return invokeStaticNoThrow(Bukkit.class, "getRegionScheduler", new Class<?>[0]);
    }

    private static Object getAsyncScheduler(Plugin plugin) {
        return getAsyncScheduler(plugin == null ? Bukkit.getServer() : plugin.getServer());
    }

    private static Object getAsyncScheduler(Server server) {
        SchedulerHandle handle = asyncSchedulerHandle;
        if (handle != null && handle.server == server) {
            return handle.scheduler;
        }

        Object scheduler = resolveAsyncScheduler(server);
        if (scheduler != null) {
            asyncSchedulerHandle = new SchedulerHandle(server, scheduler);
        }

        return scheduler;
    }

    private static Object resolveAsyncScheduler(Server server) {
        if (server != null && SERVER_GET_ASYNC_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(SERVER_GET_ASYNC_SCHEDULER, server);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (server != null) {
            Object scheduler = invokeNoThrow(server, "getAsyncScheduler", new Class<?>[0]);
            if (scheduler != null) {
                return scheduler;
            }
        }

        if (BUKKIT_GET_ASYNC_SCHEDULER != null) {
            Object scheduler = invokeNoThrow(BUKKIT_GET_ASYNC_SCHEDULER, null);
            if (scheduler != null) {
                return scheduler;
            }
        }

        return invokeStaticNoThrow(Bukkit.class, "getAsyncScheduler", new Class<?>[0]);
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

        bukkitOwned = invokeBooleanStaticNoThrow(
                Bukkit.class,
                "isOwnedByCurrentRegion",
                new Class<?>[]{World.class, int.class, int.class},
                world,
                chunkX,
                chunkZ
        );
        if (bukkitOwned != null) {
            return bukkitOwned;
        }

        Boolean serverOwned = invokeBooleanNoThrow(SERVER_IS_OWNED_WORLD_CHUNK_REGION, server, world, chunkX, chunkZ);
        if (serverOwned != null) {
            return serverOwned;
        }

        return invokeBooleanNoThrow(
                server,
                "isOwnedByCurrentRegion",
                new Class<?>[]{World.class, int.class, int.class},
                world,
                chunkX,
                chunkZ
        );
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

    private static Class<?> resolveClass(String className) {
        try {
            return Class.forName(className);
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

    private static Object invokeStaticNoThrow(Class<?> owner, String methodName, Class<?>[] parameterTypes, Object... args) {
        Method method = cachedMethod(owner, methodName, parameterTypes);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method cachedMethod(Class<?> owner, String methodName, Class<?>[] parameterTypes) {
        MethodKey key = new MethodKey(owner, methodName, parameterTypes);
        Object cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached instanceof Method method ? method : null;
        }

        Method resolved;
        try {
            resolved = owner.getMethod(methodName, parameterTypes);
        } catch (Throwable ignored) {
            resolved = null;
        }

        METHOD_CACHE.put(key, resolved == null ? METHOD_MISS : resolved);
        return resolved;
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

    private static Boolean invokeBooleanStaticNoThrow(Class<?> owner, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object value = invokeStaticNoThrow(owner, methodName, parameterTypes, args);
        if (value instanceof Boolean bool) {
            return bool;
        }

        return null;
    }

    private static Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        Method method = cachedMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ScheduleResult invokeScheduleNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return ScheduleResult.UNAVAILABLE;
        }

        Method method = cachedMethod(target.getClass(), methodName, parameterTypes);
        if (method == null) {
            return ScheduleResult.UNAVAILABLE;
        }

        try {
            return classifyScheduleOutcome(method.getReturnType() == void.class, method.invoke(target, args));
        } catch (Throwable ignored) {
            return ScheduleResult.UNAVAILABLE;
        }
    }

    static ScheduleResult classifyScheduleOutcome(boolean voidReturn, Object result) {
        if (voidReturn) {
            return ScheduleResult.SCHEDULED;
        }

        if (result instanceof Boolean scheduled) {
            return scheduled ? ScheduleResult.SCHEDULED : ScheduleResult.REJECTED;
        }

        return result == null ? ScheduleResult.REJECTED : ScheduleResult.SCHEDULED;
    }

    enum ScheduleResult {
        SCHEDULED,
        REJECTED,
        UNAVAILABLE
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

    private static final class SchedulerHandle {
        private final Server server;
        private final Object scheduler;

        private SchedulerHandle(Server server, Object scheduler) {
            this.server = server;
            this.scheduler = scheduler;
        }
    }

    private static final class MethodKey {
        private final Class<?> owner;
        private final String methodName;
        private final Class<?>[] parameterTypes;
        private final int hash;

        private MethodKey(Class<?> owner, String methodName, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
            this.hash = (owner.hashCode() * 31 + methodName.hashCode()) * 31 + Arrays.hashCode(parameterTypes);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }

            if (!(other instanceof MethodKey key)) {
                return false;
            }

            return owner == key.owner && methodName.equals(key.methodName) && Arrays.equals(parameterTypes, key.parameterTypes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
