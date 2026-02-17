package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class PlayerHandlerBase implements DirectorParameterHandler<Player> {
    protected abstract List<Player> playerOptions();

    @Override
    public KList<Player> getPossibilities() {
        return new KList<>(playerOptions());
    }

    @Override
    public String toString(Player player) {
        return player.getName();
    }

    @Override
    public Player parse(String in, boolean force) throws DirectorParsingException {
        KList<Player> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DirectorParsingException("Unable to find Player \"" + in + "\"");
        }

        Player exact = options.stream()
                .filter(player -> toString(player).equalsIgnoreCase(in))
                .findFirst()
                .orElse(null);

        if (exact != null) {
            return exact;
        }

        throw new DirectorParsingException("Unable to filter which Player \"" + in + "\"");
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Player.class);
    }

    @Override
    public String getRandomDefault() {
        return "playername";
    }
}
