package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WorldIdentity {
    private static final Method WORLD_INFO_GET_KEY = resolveWorldInfoGetKey();

    private WorldIdentity() {
    }

    public static NamespacedKey key(WorldInfo world) {
        WorldInfo requiredWorld = Objects.requireNonNull(world, "world");
        if (requiredWorld instanceof Keyed keyed) {
            return Objects.requireNonNull(keyed.getKey(), "world key");
        }

        NamespacedKey reflectedKey = invokeWorldInfoGetKey(requiredWorld);
        if (reflectedKey != null) {
            return reflectedKey;
        }

        throw new IllegalStateException("WorldInfo does not expose a namespaced key: " + requiredWorld.getName());
    }

    public static String serialize(WorldInfo world) {
        return key(world).toString();
    }

    public static NamespacedKey parse(String serialized) {
        String value = Objects.requireNonNull(serialized, "serialized").trim();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("World identity must be a fully qualified namespaced key: " + value);
        }

        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null) {
            throw new IllegalArgumentException("Invalid world identity: " + value);
        }
        return key;
    }

    public static Optional<World> resolve(String serialized) {
        return resolve(parse(serialized));
    }

    public static Optional<World> resolve(NamespacedKey key) {
        return resolve(Objects.requireNonNull(key, "key"), Bukkit.getWorlds());
    }

    static Optional<World> resolve(NamespacedKey key, List<World> worlds) {
        NamespacedKey requiredKey = Objects.requireNonNull(key, "key");
        for (World world : Objects.requireNonNull(worlds, "worlds")) {
            if (requiredKey.equals(key(world))) {
                return Optional.of(world);
            }
        }
        return Optional.empty();
    }

    private static Method resolveWorldInfoGetKey() {
        try {
            return WorldInfo.class.getMethod("getKey");
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static NamespacedKey invokeWorldInfoGetKey(WorldInfo world) {
        if (WORLD_INFO_GET_KEY == null) {
            return null;
        }
        try {
            Object value = WORLD_INFO_GET_KEY.invoke(world);
            return value instanceof NamespacedKey key ? key : null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }
}
