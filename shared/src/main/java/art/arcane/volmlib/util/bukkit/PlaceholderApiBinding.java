package art.arcane.volmlib.util.bukkit;

import art.arcane.volmlib.util.VolmLog;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * PlaceholderAPI's {@code setPlaceholders(OfflinePlayer, String)}, found through PlaceholderAPI's own class loader so
 * the host plugin needs no dependency on it. Once the host plugin is enabled it follows PlaceholderAPI being enabled
 * and disabled; until then PlaceholderAPI is looked up again at most once a second.
 */
final class PlaceholderApiBinding {
    private static final String PLUGIN = "PlaceholderAPI";
    private static final String API = "me.clip.placeholderapi.PlaceholderAPI";
    private static final String SETTER = "setPlaceholders";
    private static final long PROBE_INTERVAL_MS = 1000L;
    private static final MethodType SETTER_TYPE = MethodType.methodType(String.class, OfflinePlayer.class, String.class);

    private final Supplier<PluginManager> plugins;
    private final Supplier<Plugin> host;
    private final LongSupplier clockMs;
    private volatile MethodHandle setter;
    private volatile boolean following;
    private volatile long nextProbeAtMs;
    private volatile boolean lookupFailureLogged;
    private volatile boolean invokeFailureLogged;

    PlaceholderApiBinding(Supplier<PluginManager> plugins, Supplier<Plugin> host, LongSupplier clockMs) {
        this.plugins = Objects.requireNonNull(plugins, "plugins");
        this.host = Objects.requireNonNull(host, "host");
        this.clockMs = Objects.requireNonNull(clockMs, "clockMs");
    }

    /** The text filled for the player, or for no player when it is null; the text as written when PlaceholderAPI is absent or fails. */
    String fill(OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0) {
            return text;
        }

        MethodHandle current = following || clockMs.getAsLong() < nextProbeAtMs ? setter : probe();
        if (current == null) {
            return text;
        }

        try {
            String resolved = (String) current.invokeExact(player, text);
            return resolved == null ? text : resolved;
        } catch (Throwable throwable) {
            if (!invokeFailureLogged) {
                invokeFailureLogged = true;
                VolmLog.warning("Placeholders", "PlaceholderAPI resolution failed; text is served unresolved", throwable);
            }
            return text;
        }
    }

    private synchronized MethodHandle probe() {
        if (following || clockMs.getAsLong() < nextProbeAtMs) {
            return setter;
        }

        try {
            PluginManager manager = plugins.get();
            following = follow(manager);
            bind(manager);
        } catch (RuntimeException | LinkageError noServer) {
            setter = null;
        }
        nextProbeAtMs = clockMs.getAsLong() + PROBE_INTERVAL_MS;
        return setter;
    }

    private boolean follow(PluginManager manager) {
        Plugin owner;
        try {
            owner = host.get();
        } catch (IllegalArgumentException | IllegalStateException notAPlugin) {
            return false;
        }
        if (owner == null || !owner.isEnabled()) {
            return false;
        }

        Listener listener = new Listener() {
        };
        try {
            manager.registerEvent(PluginEnableEvent.class, listener, EventPriority.MONITOR,
                    (ignored, event) -> enabled(((PluginEnableEvent) event).getPlugin()), owner);
            manager.registerEvent(PluginDisableEvent.class, listener, EventPriority.MONITOR,
                    (ignored, event) -> disabled(((PluginDisableEvent) event).getPlugin(), owner), owner);
            return true;
        } catch (RuntimeException notEnabled) {
            return false;
        }
    }

    private synchronized void enabled(Plugin plugin) {
        if (PLUGIN.equals(plugin.getName())) {
            bind(plugin);
        }
    }

    /** Bukkit drops the host's listeners after its own disable event, so the next fill looks PlaceholderAPI up again. */
    private synchronized void disabled(Plugin plugin, Plugin owner) {
        if (plugin == owner) {
            following = false;
            nextProbeAtMs = 0L;
            setter = null;
        } else if (PLUGIN.equals(plugin.getName())) {
            setter = null;
        }
    }

    /** A plugin manager that cannot hand out PlaceholderAPI still answers whether it is enabled; VolmLib's own loader is then tried. */
    private void bind(PluginManager manager) {
        Plugin placeholderApi;
        try {
            placeholderApi = manager.getPlugin(PLUGIN);
        } catch (RuntimeException unsupported) {
            placeholderApi = null;
        }
        if (placeholderApi != null) {
            bind(placeholderApi);
        } else {
            setter = manager.isPluginEnabled(PLUGIN) ? resolve(PlaceholderApiBinding.class.getClassLoader()) : null;
        }
    }

    private void bind(Plugin placeholderApi) {
        setter = placeholderApi.isEnabled() ? resolve(placeholderApi.getClass().getClassLoader()) : null;
    }

    /** Older PlaceholderAPI releases only have the {@code Player} overload. */
    private MethodHandle resolve(ClassLoader loader) {
        try {
            Class<?> api = Class.forName(API, true, loader);
            try {
                return MethodHandles.publicLookup().findStatic(api, SETTER, SETTER_TYPE);
            } catch (NoSuchMethodException older) {
                return MethodHandles.publicLookup()
                        .findStatic(api, SETTER, SETTER_TYPE.changeParameterType(0, Player.class))
                        .asType(SETTER_TYPE);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
            if (!lookupFailureLogged) {
                lookupFailureLogged = true;
                VolmLog.warning("Placeholders", "PlaceholderAPI is enabled but " + API + "#" + SETTER + " is missing", incompatible);
            }
            return null;
        }
    }
}
