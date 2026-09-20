package art.arcane.volmlib.nativelib.entity;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.List;

public interface VirtualPlayer {
    void spawn(Player viewer);
    void removeFromTab(Player viewer);
    void destroy(Player viewer);
    boolean look(float yaw, float pitch, Player viewer);
    boolean move(Location location, boolean onGround, List<Player> viewers);
    void hurt(float yaw, List<Player> viewers);
}
