package art.arcane.volmlib.util.decree.handlers.base;

import art.arcane.volmlib.util.decree.DecreeParameterHandler;
import org.bukkit.entity.Player;

public final class NullablePlayerHandlerBase {
    private NullablePlayerHandlerBase() {
    }

    public static Player parseNullable(DecreeParameterHandler<Player> handler, String in) {
        return handler.getPossibilities(in).stream()
                .filter(player -> player.getName().equalsIgnoreCase(in))
                .findFirst()
                .orElse(null);
    }
}
