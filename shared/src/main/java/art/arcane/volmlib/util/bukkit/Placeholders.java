package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Placeholders {
    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";
    private static final String PLACEHOLDER_API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";
    private static final String SETTER_METHOD = "setPlaceholders";
    private static final long PROBE_INTERVAL_MS = 1000L;
    private static final MethodType SETTER_TYPE = MethodType.methodType(String.class, Player.class, String.class);
    private static final Logger LOGGER = Logger.getLogger(Placeholders.class.getName());

    private static volatile Setter placeholderSetter;
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

        Setter setter = resolveSetter();

        if (setter == null) {
            return text;
        }

        try {
            return setter.resolve(player, text, text);
        } catch (Throwable throwable) {
            if (!invokeFailureLogged) {
                invokeFailureLogged = true;
                LOGGER.log(Level.WARNING, "PlaceholderAPI resolution failed; text is served unresolved", throwable);
            }
        }

        return text;
    }

    private static Setter resolveSetter() {
        if (System.currentTimeMillis() < nextProbeAtMs) {
            return placeholderSetter;
        }

        return probe();
    }

    private static synchronized Setter probe() {
        if (System.currentTimeMillis() < nextProbeAtMs) {
            return placeholderSetter;
        }

        Setter resolved = lookup();
        placeholderSetter = resolved;
        nextProbeAtMs = System.currentTimeMillis() + PROBE_INTERVAL_MS;
        return resolved;
    }

    private static Setter lookup() {
        Class<?> api = resolveApiClass();

        if (api == null) {
            return null;
        }

        Setter cached = placeholderSetter;

        if (cached != null && cached.owner() == api) {
            return cached;
        }

        try {
            Method method = api.getMethod(SETTER_METHOD, Player.class, String.class);
            return new Setter(api, method, bindHandle(method));
        } catch (Throwable throwable) {
            if (!lookupFailureLogged) {
                lookupFailureLogged = true;
                LOGGER.log(Level.WARNING, "PlaceholderAPI is enabled but " + PLACEHOLDER_API_CLASS + "#" + SETTER_METHOD + " is missing", throwable);
            }

            return null;
        }
    }

    /**
     * A direct handle removes the boxed argument array that {@link Method#invoke} allocates on
     * every call. Exotic classloader setups can refuse the unreflect; those fall back to the
     * reflective call rather than losing placeholder resolution entirely.
     */
    private static MethodHandle bindHandle(Method method) {
        try {
            return MethodHandles.publicLookup().unreflect(method).asType(SETTER_TYPE);
        } catch (Throwable ignored) {
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

    private record Setter(Class<?> owner, Method method, MethodHandle handle) {
        private String resolve(Player player, String text, String fallback) throws Throwable {
            if (handle != null) {
                String resolved = (String) handle.invokeExact(player, text);
                return resolved == null ? fallback : resolved;
            }

            return method.invoke(null, player, text) instanceof String resolved ? resolved : fallback;
        }
    }
}
