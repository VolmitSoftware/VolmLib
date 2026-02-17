package art.arcane.volmlib.util.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SchedulerUtils {
    private SchedulerUtils() {
    }

    public interface TaskHandle {
        void cancel();

        boolean isCancelled();
    }

    public static boolean runSync(Plugin plugin, Runnable runnable) {
        return runGlobal(plugin, runnable);
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable) {
        TaskHandle taskHandle = runAsyncTask(plugin, runnable);
        return taskHandle != null && !taskHandle.isCancelled();
    }

    public static TaskHandle scheduleSyncTimer(Plugin plugin, long period, long repetitions, Consumer<Long> onIteration, Runnable onFinish) {
        if (!isPluginActive(plugin) || onIteration == null || onFinish == null) {
            return new NoopTaskHandle(true);
        }

        if (repetitions <= 0) {
            scheduleSyncTask(plugin, Math.max(1L, period), onFinish, false);
            return new NoopTaskHandle(true);
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        long safePeriod = Math.max(1L, period);
        long[] currentIterations = {0L};
        TaskHandle[] reference = new TaskHandle[1];

        reference[0] = scheduleSyncTask(plugin, safePeriod, () -> {
            if (cancelled.get()) {
                return;
            }

            if (currentIterations[0] >= repetitions) {
                onFinish.run();
                cancelled.set(true);
                if (reference[0] != null) {
                    reference[0].cancel();
                }
                return;
            }

            onIteration.accept(currentIterations[0]);
            currentIterations[0]++;
        }, false);

        return new AtomicTaskHandle(cancelled, reference[0]);
    }

    public static TaskHandle scheduleSyncTask(Plugin plugin, long period, Runnable onIteration, boolean delayStart) {
        if (!isPluginActive(plugin) || onIteration == null) {
            return new NoopTaskHandle(true);
        }

        long safePeriod = Math.max(1L, period);
        long initialDelay = delayStart ? safePeriod : 0L;

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicBoolean started = new AtomicBoolean(false);
        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (cancelled.get() || !isPluginActive(plugin)) {
                cancelled.set(true);
                return;
            }

            if (started.get() || !delayStart) {
                onIteration.run();
            }
            started.set(true);

            if (!cancelled.get()) {
                scheduleSyncDelayed(plugin, loop[0], safePeriod, cancelled);
            }
        };

        scheduleSyncDelayed(plugin, loop[0], initialDelay, cancelled);
        return new AtomicTaskHandle(cancelled, null);
    }

    public static TaskHandle runAsyncTask(Plugin plugin, Runnable runnable) {
        if (!isPluginActive(plugin) || runnable == null) {
            return new NoopTaskHandle(true);
        }

        if (FoliaScheduler.runAsync(plugin, runnable)) {
            return new NoopTaskHandle(false);
        }

        try {
            BukkitTask task = Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
            return new BukkitTaskHandle(task);
        } catch (IllegalPluginAccessException exception) {
            return new NoopTaskHandle(true);
        } catch (UnsupportedOperationException exception) {
            return new NoopTaskHandle(true);
        }
    }

    public static boolean runGlobal(Plugin plugin, Runnable runnable) {
        if (plugin == null || runnable == null || !isPluginActive(plugin)) {
            return false;
        }

        if (FoliaScheduler.runGlobal(plugin, runnable)) {
            return true;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return true;
        } catch (IllegalPluginAccessException | UnsupportedOperationException exception) {
            return false;
        }
    }

    public static boolean runEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (plugin == null || entity == null || runnable == null || !isPluginActive(plugin)) {
            return false;
        }

        if (FoliaScheduler.runEntity(plugin, entity, runnable)) {
            return true;
        }

        if (FoliaScheduler.isFolia(plugin.getServer())) {
            plugin.getLogger().warning("Failed to run entity task on Folia for plugin " + plugin.getName()
                    + "; refusing unsafe global fallback.");
            return false;
        }

        return runGlobal(plugin, runnable);
    }

    public static void cancelPluginTasks(Plugin plugin) {
        if (plugin == null) {
            return;
        }

        FoliaScheduler.cancelTasks(plugin);

        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (UnsupportedOperationException | IllegalPluginAccessException exception) {
            return;
        }
    }

    private static void scheduleSyncDelayed(Plugin plugin, Runnable runnable, long delayTicks, AtomicBoolean cancelled) {
        if (cancelled.get() || !isPluginActive(plugin) || runnable == null) {
            cancelled.set(true);
            return;
        }

        long safeDelay = Math.max(0L, delayTicks);
        boolean scheduled = FoliaScheduler.runGlobal(plugin, runnable, safeDelay);
        if (scheduled) {
            return;
        }

        try {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelay);
        } catch (IllegalPluginAccessException exception) {
            cancelled.set(true);
            if (!isPluginActive(plugin)) {
                return;
            }

            throw new IllegalStateException("Failed to schedule sync task while plugin is enabled.", exception);
        } catch (UnsupportedOperationException exception) {
            throw new IllegalStateException("Failed to schedule sync task on Folia-safe scheduler.", exception);
        }
    }

    private static boolean isPluginActive(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    private static class AtomicTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled;
        private final TaskHandle delegate;

        private AtomicTaskHandle(AtomicBoolean cancelled, TaskHandle delegate) {
            this.cancelled = cancelled;
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            if (delegate != null) {
                delegate.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || (delegate != null && delegate.isCancelled());
        }
    }

    private static class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask delegate;

        private BukkitTaskHandle(BukkitTask delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
    }

    private static class NoopTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled;

        private NoopTaskHandle(boolean initiallyCancelled) {
            this.cancelled = new AtomicBoolean(initiallyCancelled);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }
}
