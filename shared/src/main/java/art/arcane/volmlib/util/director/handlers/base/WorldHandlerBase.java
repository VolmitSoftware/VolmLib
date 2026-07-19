package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

public abstract class WorldHandlerBase {
    protected abstract String excludedPrefix();

    protected List<World> worldOptions() {
        List<World> options = new ArrayList<>();
        String prefix = excludedPrefix() == null ? "" : excludedPrefix().toLowerCase();

        for (World world : Bukkit.getWorlds()) {
            String keyPath = WorldIdentity.key(world).getKey().toLowerCase();
            if (prefix.isEmpty() || !keyPath.startsWith(prefix)) {
                options.add(world);
            }
        }

        return options;
    }

    public KList<World> getPossibilities() {
        return new KList<>(worldOptions());
    }

    public String toString(World world) {
        return WorldIdentity.serialize(world);
    }

    public World parse(String in, boolean force) throws DirectorParsingException {
        String value = in == null ? "" : in.trim();
        List<World> options = worldOptions();
        if (!value.contains(":")) {
            World match = null;
            for (World world : options) {
                boolean matchesName = world.getName().equalsIgnoreCase(value);
                boolean matchesKeyPath = WorldIdentity.key(world).getKey().equalsIgnoreCase(value);
                if (!matchesName && !matchesKeyPath) {
                    continue;
                }
                if (match != null && match != world) {
                    throw new DirectorParsingException("Unable to filter which World \"" + in + "\"");
                }
                match = world;
            }

            if (match != null) {
                return match;
            }
            throw new DirectorParsingException("Unable to find World \"" + in + "\"");
        }

        NamespacedKey key;
        try {
            key = WorldIdentity.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new DirectorParsingException("Invalid world key \"" + in + "\"");
        }

        for (World world : options) {
            if (key.equals(WorldIdentity.key(world))) {
                return world;
            }
        }
        throw new DirectorParsingException("Unable to find World \"" + in + "\"");
    }

    public boolean supports(Class<?> type) {
        return type.equals(World.class);
    }

    public String getRandomDefault() {
        return "minecraft:overworld";
    }
}
