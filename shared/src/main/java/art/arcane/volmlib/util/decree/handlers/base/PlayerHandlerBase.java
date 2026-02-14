package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import art.arcane.volmlib.util.decree.exceptions.DecreeParsingException;
import org.bukkit.entity.Player;

import java.util.List;

public abstract class PlayerHandlerBase implements DecreeParameterHandler<Player> {
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
    public Player parse(String in, boolean force) throws DecreeParsingException {
        KList<Player> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DecreeParsingException("Unable to find Player \"" + in + "\"");
        }

        Player exact = options.stream()
                .filter(player -> toString(player).equalsIgnoreCase(in))
                .findFirst()
                .orElse(null);

        if (exact != null) {
            return exact;
        }

        throw new DecreeParsingException("Unable to filter which Player \"" + in + "\"");
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
