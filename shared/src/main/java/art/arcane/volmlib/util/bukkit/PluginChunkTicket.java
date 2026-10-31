package art.arcane.volmlib.util.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PluginChunkTicket implements AutoCloseable {
    private static final Map<Key, Hold> HOLDS = new HashMap<>();

    private final Key key;
    private boolean closed;

    private PluginChunkTicket(Key key) {
        this.key = key;
    }

    public static PluginChunkTicket acquire(Plugin plugin, Location location) {
        World world = Objects.requireNonNull(location.getWorld(), "world");
        Key key = new Key(Objects.requireNonNull(plugin, "plugin"), world,
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
        synchronized (HOLDS) {
            if (!world.isChunkLoaded(key.x(), key.z())) {
                throw new IllegalStateException("Cannot retain an unloaded chunk at " + location);
            }
            Hold hold = HOLDS.get(key);
            if (hold == null) {
                hold = new Hold(world.addPluginChunkTicket(key.x(), key.z(), plugin));
                HOLDS.put(key, hold);
            }
            hold.references++;
            return new PluginChunkTicket(key);
        }
    }

    @Override
    public void close() {
        synchronized (HOLDS) {
            if (closed) {
                return;
            }
            Hold hold = HOLDS.get(key);
            if (hold.references == 1) {
                if (hold.managed) {
                    key.world().removePluginChunkTicket(key.x(), key.z(), key.plugin());
                }
                HOLDS.remove(key);
            } else {
                hold.references--;
            }
            closed = true;
        }
    }

    private record Key(Plugin plugin, World world, int x, int z) {
    }

    private static final class Hold {
        private final boolean managed;
        private int references;

        private Hold(boolean managed) {
            this.managed = managed;
        }
    }
}
