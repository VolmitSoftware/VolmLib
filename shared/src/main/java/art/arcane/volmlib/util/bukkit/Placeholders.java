package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Placeholders {
    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";
    private static final String PLACEHOLDER_API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";
    private static final String SETTER_METHOD = "setPlaceholders";
    private static final long PROBE_INTERVAL_MS = 1000L;
    private static final Logger LOGGER = Logger.getLogger(Placeholders.class.getName());

    private static volatile Method placeholderSetter;
    private static volatile long nextProbeAtMs;
    private static volatile boolean lookupFailureLogged;
    private static volatile boolean invokeFailureLogged;

    private Placeholders() {
    }

    public static boolean containsPlaceholder(String text) {
        return text != null && text.indexOf('%') >= 0;
    }

    public static String setPlaceholders(Player player, String text) {
        if (player == null || !containsPlaceholder(text)) {
            return text;
        }

        Method method = resolveSetterMethod();

        if (method == null) {
            return text;
        }

        try {
            Object resolved = method.invoke(null, player, text);

            if (resolved instanceof String stringValue) {
                return stringValue;
            }
        } catch (Throwable throwable) {
            if (!invokeFailureLogged) {
                invokeFailureLogged = true;
                LOGGER.log(Level.WARNING, "PlaceholderAPI resolution failed; text is served unresolved", throwable);
            }
        }

        return text;
    }

    private static Method resolveSetterMethod() {
        if (System.currentTimeMillis() < nextProbeAtMs) {
            return placeholderSetter;
        }

        return probe();
    }

    private static synchronized Method probe() {
        if (System.currentTimeMillis() < nextProbeAtMs) {
            return placeholderSetter;
        }

        Method resolved = lookup();
        placeholderSetter = resolved;
        nextProbeAtMs = System.currentTimeMillis() + PROBE_INTERVAL_MS;
        return resolved;
    }

    private static Method lookup() {
        Class<?> api = resolveApiClass();

        if (api == null) {
            return null;
        }

        Method cached = placeholderSetter;

        if (cached != null && cached.getDeclaringClass() == api) {
            return cached;
        }

        try {
            return api.getMethod(SETTER_METHOD, Player.class, String.class);
        } catch (Throwable throwable) {
            if (!lookupFailureLogged) {
                lookupFailureLogged = true;
                LOGGER.log(Level.WARNING, "PlaceholderAPI is enabled but " + PLACEHOLDER_API_CLASS + "#" + SETTER_METHOD + " is missing", throwable);
            }

            return null;
        }
    }

    private static Class<?> resolveApiClass() {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled(PLACEHOLDER_API_PLUGIN)) {
                return null;
            }

            return Class.forName(PLACEHOLDER_API_CLASS);
        } catch (Throwable throwable) {
            return null;
        }
    }
}
