package art.arcane.volmlib.util.director.handlers.base;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BukkitPlayerHandlerBase extends PlayerHandlerBase {
    @Override
    protected List<Player> playerOptions() {
        return new ArrayList<>(Bukkit.getOnlinePlayers());
    }
}
