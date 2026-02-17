package art.arcane.volmlib.util.director;

import org.bukkit.World;
import org.bukkit.entity.Player;

public interface DirectorExecutorBase {
    Player player();

    default World world() {
        Player player = player();
        return player == null ? null : player.getWorld();
    }

    default <T> T get(T value, T ifUndefined) {
        return value == null ? ifUndefined : value;
    }
}
