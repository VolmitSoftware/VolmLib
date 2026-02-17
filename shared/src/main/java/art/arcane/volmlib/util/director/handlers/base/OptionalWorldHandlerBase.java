package art.arcane.volmlib.util.director.handlers.base;

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
            if (prefix.isEmpty() || !world.getName().toLowerCase().startsWith(prefix)) {
                options.add(world.getName());
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
        return in;
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
