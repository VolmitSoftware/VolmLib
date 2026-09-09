package art.arcane.volmlib.util.bukkit;

import org.bukkit.Location;
import org.bukkit.RegionAccessor;
import org.bukkit.entity.Entity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.Consumer;

public final class BukkitEntitySpawns {
    private static final Method SPAWN = resolveSpawn();
    private static final Class<?> CALLBACK = SPAWN.getParameterTypes()[2];

    private BukkitEntitySpawns() {
    }

    public static <T extends Entity> T spawn(RegionAccessor region, Location location, Class<T> type,
                                             Consumer<? super T> initializer) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(initializer, "initializer");
        Object callback = CALLBACK == Consumer.class ? initializer : Proxy.newProxyInstance(
                CALLBACK.getClassLoader(), new Class<?>[]{CALLBACK}, (proxy, method, arguments) -> {
                    if (method.getName().equals("accept") && method.getParameterCount() == 1) {
                        initializer.accept(type.cast(arguments[0]));
                        return null;
                    }
                    return switch (method.getName()) {
                        case "equals" -> proxy == arguments[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "Bukkit entity initializer";
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                });
        try {
            return type.cast(SPAWN.invoke(region, location, type, callback));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not access the Bukkit entity spawn method", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Bukkit entity spawning failed", cause);
        }
    }

    private static Method resolveSpawn() {
        Method legacy = null;
        for (Method method : RegionAccessor.class.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getName().equals("spawn") && parameters.length == 3
                    && parameters[0] == Location.class && parameters[1] == Class.class
                    && (parameters[2] == Consumer.class || parameters[2].getName().equals("org.bukkit.util.Consumer"))) {
                if (parameters[2] == Consumer.class) {
                    return method;
                }
                legacy = method;
            }
        }
        if (legacy != null) {
            return legacy;
        }
        throw new IllegalStateException("The server has no supported entity spawn initializer");
    }
}
