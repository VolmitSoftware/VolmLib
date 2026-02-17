package art.arcane.volmlib.util.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SchedulerRuntime {
    private static final long TICK_MS = 50L;

    private final Supplier<Plugin> pluginSupplier;
    private final Consumer<Runnable> asyncScheduler;
    private final Consumer<String> verboseLogger;
    private final Consumer<String> warningLogger;
    private final Consumer<Throwable> errorHandler;
    private final AtomicInteger taskIds;
    private final Map<Integer, Runnable> repeatingCancellers;
    private final StartupQueueSupport startupQueue;

    public SchedulerRuntime(
            Supplier<Plugin> pluginSupplier,
            Consumer<Runnable> asyncScheduler,
            Consumer<String> verboseLogger,
            Consumer<String> warningLogger,
            Consumer<Throwable> errorHandler
    ) {
        this.pluginSupplier = pluginSupplier;
        this.asyncScheduler = asyncScheduler;
        this.verboseLogger = verboseLogger;
        this.warningLogger = warningLogger;
        this.errorHandler = errorHandler;
        this.taskIds = new AtomicInteger(1);
        this.repeatingCancellers = new ConcurrentHashMap<>();
        this.startupQueue = new StartupQueueSupport();
    }

    public void attemptAsync(JSupport.ThrowingRunnable runnable) {
        JSupport.attemptAsync(runnable, this::scheduleAsyncFallback);
    }

    public void executeAfterStartupQueue(Consumer<Runnable> syncScheduler) {
        JSupport.executeAfterStartupQueue(startupQueue, syncScheduler, this::scheduleAsyncFallback);
    }

    public void enqueueAfterStartupSync(Runnable runnable, Consumer<Runnable> syncScheduler) {
        JSupport.enqueueAfterStartupSync(startupQueue, runnable, syncScheduler);
    }

    public void enqueueAfterStartupAsync(Runnable runnable) {
        JSupport.enqueueAfterStartupAsync(startupQueue, runnable, this::scheduleAsyncFallback);
    }

    public void cancelPluginTasks() {
        Plugin plugin = plugin();
        if (plugin == null) {
            return;
        }

        for (Runnable cancelAction : repeatingCancellers.values()) {
            try {
                cancelAction.run();
            } catch (Throwable ex) {
                verbose("Failed to run cancel action: " + ex.getClass().getSimpleName()
                        + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            }
        }
        repeatingCancellers.clear();

        FoliaScheduler.cancelTasks(plugin);

        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (UnsupportedOperationException | IllegalPluginAccessException ex) {
            verbose("Skipping BukkitScheduler#cancelTasks for plugin " + plugin.getName() + " on this server.");
        }
    }

    public void s(Runnable runnable) {
        if (!isPluginActive()) {
            return;
        }

        if (!runGlobalImmediate(runnable)) {
            Plugin plugin = plugin();
            if (plugin == null) {
                return;
            }

            try {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable);
            } catch (IllegalPluginAccessException e) {
                if (!isPluginActive()) {
                    return;
                }

                throw new IllegalStateException("Failed to schedule global sync task while plugin is enabled.", e);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("Failed to schedule global sync task on this server (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
            }
        }
    }

    public void s(Runnable runnable, int delayTicks) {
        if (delayTicks <= 0) {
            s(runnable);
            return;
        }

        if (!isPluginActive()) {
            return;
        }

        if (!runGlobalDelayed(runnable, delayTicks)) {
            Plugin plugin = plugin();
            if (plugin == null) {
                return;
            }

            try {
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, runnable, delayTicks);
            } catch (IllegalPluginAccessException e) {
                if (!isPluginActive()) {
                    return;
                }

                throw new IllegalStateException("Failed to schedule delayed global sync task while plugin is enabled.", e);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("Failed to schedule delayed global sync task on this server (Folia scheduler unavailable, BukkitScheduler unsupported).", e);
            }
        }
    }

    public int sr(Runnable runnable, int intervalTicks) {
        int safeInterval = Math.max(1, intervalTicks);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginActive()) {
                repeatingCancellers.remove(taskId);
                return;
            }

            runnable.run();
            if (state.cancelled || !isPluginActive()) {
                repeatingCancellers.remove(taskId);
                return;
            }

            s(loop[0], safeInterval);
        };

        s(loop[0]);
        return taskId;
    }

    public void csr(int id) {
        cancelRepeatingTask(id);
    }

    public void a(Runnable runnable, int delayTicks) {
        if (!isPluginActive()) {
            return;
        }

        if (delayTicks <= 0) {
            if (!runAsyncImmediate(runnable)) {
                scheduleAsyncFallback(runnable);
            }
            return;
        }

        if (!runAsyncDelayed(runnable, delayTicks)) {
            scheduleAsyncFallback(() -> {
                if (JSupport.sleep(ticksToMilliseconds(delayTicks))) {
                    runnable.run();
                }
            });
        }
    }

    public int ar(Runnable runnable, int intervalTicks) {
        int safeInterval = Math.max(1, intervalTicks);
        RepeatingState state = new RepeatingState();
        int taskId = trackRepeatingTask(() -> state.cancelled = true);

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (state.cancelled || !isPluginActive()) {
                repeatingCancellers.remove(taskId);
                return;
            }

            runnable.run();
            if (state.cancelled || !isPluginActive()) {
                repeatingCancellers.remove(taskId);
                return;
            }

            a(loop[0], safeInterval);
        };

        a(loop[0], 0);
        return taskId;
    }

    public void car(int id) {
        cancelRepeatingTask(id);
    }

    public boolean isFoliaThreading() {
        return FoliaScheduler.isFoliaThreading(Bukkit.getServer());
    }

    public boolean isOwnedByCurrentRegion(Entity entity) {
        return FoliaScheduler.isOwnedByCurrentRegion(entity);
    }

    public boolean runEntity(Entity entity, Runnable runnable) {
        if (entity == null || runnable == null || !isPluginActive()) {
            return false;
        }

        if (isFoliaThreading()) {
            if (isOwnedByCurrentRegion(entity)) {
                runnable.run();
                return true;
            }

            return runEntityImmediate(entity, runnable);
        }

        if (FoliaScheduler.isPrimaryThread()) {
            runnable.run();
            return true;
        }

        return runEntityImmediate(entity, runnable);
    }

    public boolean runEntity(Entity entity, Runnable runnable, int delayTicks) {
        if (entity == null || runnable == null || !isPluginActive()) {
            return false;
        }

        if (delayTicks <= 0) {
            return runEntity(entity, runnable);
        }

        if (isFoliaThreading() && runEntityDelayed(entity, runnable, delayTicks)) {
            return true;
        }

        s(() -> runEntity(entity, runnable), delayTicks);
        return true;
    }

    public boolean teleport(Entity entity, Location location) {
        return teleport(entity, location, null);
    }

    public boolean teleport(Entity entity, Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (entity == null || location == null) {
            return false;
        }

        if (isFoliaThreading()) {
            Object asyncWithCause = null;
            if (cause != null) {
                asyncWithCause = invokeNoThrow(
                        entity,
                        "teleportAsync",
                        new Class<?>[]{Location.class, PlayerTeleportEvent.TeleportCause.class},
                        location,
                        cause
                );
            }

            if (asyncWithCause != null) {
                return true;
            }

            Object async = invokeNoThrow(entity, "teleportAsync", new Class<?>[]{Location.class}, location);
            if (async != null) {
                return true;
            }
        }

        try {
            if (cause != null) {
                return entity.teleport(location, cause);
            }

            return entity.teleport(location);
        } catch (UnsupportedOperationException e) {
            warn("Failed to teleport entity synchronously on this server; teleportAsync was unavailable. Entity="
                    + entity.getUniqueId() + " world=" + (location.getWorld() == null ? "null" : location.getWorld().getName()));
            return false;
        }
    }

    public boolean runAt(Location location, Runnable runnable) {
        if (location == null || runnable == null) {
            return false;
        }

        if (runRegionImmediate(location, runnable)) {
            return true;
        }

        if (isFoliaThreading()) {
            World world = location.getWorld();
            verbose("Failed to schedule immediate region task at "
                    + (world == null ? "null" : world.getName())
                    + "@" + (location.getBlockX() >> 4) + "," + (location.getBlockZ() >> 4) + " on Folia.");
            return false;
        }

        s(runnable);
        return true;
    }

    public boolean runAt(Location location, Runnable runnable, int delayTicks) {
        if (location == null || runnable == null) {
            return false;
        }

        if (delayTicks <= 0) {
            return runAt(location, runnable);
        }

        if (runRegionDelayed(location, runnable, delayTicks)) {
            return true;
        }

        if (isFoliaThreading()) {
            World world = location.getWorld();
            verbose("Failed to schedule delayed region task at "
                    + (world == null ? "null" : world.getName())
                    + "@" + (location.getBlockX() >> 4) + "," + (location.getBlockZ() >> 4)
                    + " (" + delayTicks + "t) on Folia.");
            return false;
        }

        s(runnable, delayTicks);
        return true;
    }

    private int trackRepeatingTask(Runnable cancelAction) {
        int id = taskIds.getAndIncrement();
        repeatingCancellers.put(id, cancelAction);
        return id;
    }

    private void cancelRepeatingTask(int id) {
        Runnable cancelAction = repeatingCancellers.remove(id);
        if (cancelAction != null) {
            cancelAction.run();
        }
    }

    private long ticksToMilliseconds(int ticks) {
        return Math.max(0L, ticks) * TICK_MS;
    }

    private void scheduleAsyncFallback(Runnable runnable) {
        if (runnable == null || asyncScheduler == null) {
            return;
        }

        asyncScheduler.accept(runnable);
    }

    private boolean runGlobalImmediate(Runnable runnable) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runGlobal(plugin, runnable);
    }

    private boolean runGlobalDelayed(Runnable runnable, int delayTicks) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runGlobal(plugin, runnable, Math.max(0, delayTicks));
    }

    private boolean runRegionImmediate(Location location, Runnable runnable) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runRegion(plugin, location, runnable);
    }

    private boolean runRegionDelayed(Location location, Runnable runnable, int delayTicks) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runRegion(plugin, location, runnable, Math.max(0, delayTicks));
    }

    private boolean runAsyncImmediate(Runnable runnable) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runAsync(plugin, runnable);
    }

    private boolean runAsyncDelayed(Runnable runnable, int delayTicks) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runAsync(plugin, runnable, Math.max(0, delayTicks));
    }

    private boolean runEntityImmediate(Entity entity, Runnable runnable) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runEntity(plugin, entity, runnable);
    }

    private boolean runEntityDelayed(Entity entity, Runnable runnable, int delayTicks) {
        Plugin plugin = plugin();
        return plugin != null && FoliaScheduler.runEntity(plugin, entity, runnable, Math.max(0, delayTicks));
    }

    private Object invokeNoThrow(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (Throwable ex) {
            verbose("Reflective call failed for method '" + methodName + "' on " + target.getClass().getName()
                    + ": " + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
            handleError(ex);
            return null;
        }
    }

    private Plugin plugin() {
        return pluginSupplier == null ? null : pluginSupplier.get();
    }

    private boolean isPluginActive() {
        Plugin plugin = plugin();
        return plugin != null && plugin.isEnabled();
    }

    private void verbose(String message) {
        if (message == null) {
            return;
        }

        if (verboseLogger != null) {
            verboseLogger.accept(message);
        }
    }

    private void warn(String message) {
        if (message == null) {
            return;
        }

        if (warningLogger != null) {
            warningLogger.accept(message);
        }
    }

    private void handleError(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        if (errorHandler != null) {
            errorHandler.accept(throwable);
        }
    }

    private static final class RepeatingState {
        private volatile boolean cancelled;
    }
}
