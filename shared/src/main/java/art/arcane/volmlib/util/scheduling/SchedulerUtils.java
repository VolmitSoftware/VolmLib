package art.arcane.volmlib.util.scheduling;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtils {
    private SchedulerUtils() {
    }

    public static boolean runSync(Plugin plugin, Runnable runnable) {
        if (!isPluginActive(plugin) || runnable == null) {
            return false;
        }

        if (FoliaScheduler.runGlobal(plugin, runnable)) {
            return true;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, runnable);
            return true;
        } catch (IllegalPluginAccessException | UnsupportedOperationException ignored) {
            return false;
        }
    }

    public static boolean runAsync(Plugin plugin, Runnable runnable) {
        if (!isPluginActive(plugin) || runnable == null) {
            return false;
        }

        if (FoliaScheduler.runAsync(plugin, runnable)) {
            return true;
        }

        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
            return true;
        } catch (IllegalPluginAccessException | UnsupportedOperationException ignored) {
            return false;
        }
    }

    private static boolean isPluginActive(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }
}
