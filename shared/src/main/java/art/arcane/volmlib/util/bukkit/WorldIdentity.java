package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.generator.WorldInfo;

import java.util.Objects;
import java.util.Optional;

public final class WorldIdentity {
    private WorldIdentity() {
    }

    public static NamespacedKey key(WorldInfo world) {
        return Objects.requireNonNull(world, "world").getKey();
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
        return Optional.ofNullable(Bukkit.getWorld(Objects.requireNonNull(key, "key")));
    }
}
