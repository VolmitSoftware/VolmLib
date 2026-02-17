package art.arcane.volmlib.util.director.handlers.base;

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
            if (prefix.isEmpty() || !world.getName().toLowerCase().startsWith(prefix)) {
                options.add(world);
            }
        }

        return options;
    }

    public KList<World> getPossibilities() {
        return new KList<>(worldOptions());
    }

    public String toString(World world) {
        return world.getName();
    }

    public World parse(String in, boolean force) throws DirectorParsingException {
        List<World> options = new ArrayList<>();
        for (World world : worldOptions()) {
            String name = toString(world);
            if (name.equalsIgnoreCase(in) || name.toLowerCase().contains(in.toLowerCase()) || in.toLowerCase().contains(name.toLowerCase())) {
                options.add(world);
            }
        }

        if (options.isEmpty()) {
            throw new DirectorParsingException("Unable to find World \"" + in + "\"");
        }

        for (World world : options) {
            if (toString(world).equalsIgnoreCase(in)) {
                return world;
            }
        }

        throw new DirectorParsingException("Unable to filter which World \"" + in + "\"");
    }

    public boolean supports(Class<?> type) {
        return type.equals(World.class);
    }

    public String getRandomDefault() {
        return "world";
    }
}
