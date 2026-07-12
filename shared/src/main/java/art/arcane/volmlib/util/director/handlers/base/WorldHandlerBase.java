package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Bukkit;
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
        World world;
        try {
            world = WorldIdentity.resolve(in).orElse(null);
        } catch (IllegalArgumentException exception) {
            throw new DirectorParsingException("Invalid world key \"" + in + "\"");
        }

        if (world == null || !worldOptions().contains(world)) {
            throw new DirectorParsingException("Unable to find World \"" + in + "\"");
        }
        return world;
    }

    public boolean supports(Class<?> type) {
        return type.equals(World.class);
    }

    public String getRandomDefault() {
        return "minecraft:overworld";
    }
}
