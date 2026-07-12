package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Bukkit;
import org.bukkit.World;

public abstract class OptionalWorldHandlerBase implements DirectorParameterHandler<String> {
    protected abstract String excludedPrefix();

    @Override
    public KList<String> getPossibilities() {
        KList<String> options = new KList<>();
        options.add("ALL");
        String prefix = excludedPrefix() == null ? "" : excludedPrefix().toLowerCase();

        for (World world : Bukkit.getWorlds()) {
            String keyPath = WorldIdentity.key(world).getKey().toLowerCase();
            if (prefix.isEmpty() || !keyPath.startsWith(prefix)) {
                options.add(WorldIdentity.serialize(world));
            }
        }

        return options;
    }

    @Override
    public String toString(String world) {
        return world;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        if ("ALL".equalsIgnoreCase(in)) {
            return "ALL";
        }

        World world;
        try {
            world = WorldIdentity.resolve(in).orElse(null);
        } catch (IllegalArgumentException exception) {
            throw new DirectorParsingException("Invalid world key \"" + in + "\"");
        }

        String serialized = world == null ? null : WorldIdentity.serialize(world);
        if (serialized == null || !getPossibilities().contains(serialized)) {
            throw new DirectorParsingException("Unable to find World \"" + in + "\"");
        }
        return serialized;
    }

    @Override
    public boolean supports(Class<?> type) {
        return false;
    }

    @Override
    public String getRandomDefault() {
        return "ALL";
    }
}
