package art.arcane.volmlib.util.director.handlers.base;

import art.arcane.volmlib.util.director.DirectorParameterHandler;
import org.bukkit.entity.Player;

public final class NullablePlayerHandlerBase {
    private NullablePlayerHandlerBase() {
    }

    public static Player parseNullable(DirectorParameterHandler<Player> handler, String in) {
        return handler.getPossibilities(in).stream()
                .filter(player -> player.getName().equalsIgnoreCase(in))
                .findFirst()
                .orElse(null);
    }
}
