package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public interface Events extends Listener, EventExecutor {

    static <T extends Event> Events listen(Plugin plugin, Class<T> type, Consumer<T> listener) {
        return listen(plugin, type, EventPriority.NORMAL, listener);
    }

    static <T extends Event> Events listen(Plugin plugin, Class<T> type, EventPriority priority, Consumer<T> listener) {
        if (plugin == null || !plugin.isEnabled() || type == null || listener == null) {
            return ($, event) -> {
            };
        }

        Events events = ($, event) -> {
            if (!type.isInstance(event)) {
                return;
            }

            listener.accept(type.cast(event));
        };
        Bukkit.getPluginManager().registerEvent(type, events, priority, events, plugin);
        return events;
    }

    default void unregister() {
        HandlerList.unregisterAll(this);
    }
}
