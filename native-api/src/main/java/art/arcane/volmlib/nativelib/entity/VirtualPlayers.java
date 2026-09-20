package art.arcane.volmlib.nativelib.entity;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface VirtualPlayers {
    VirtualPlayer create(Player owner, Location location, int skinLayerMask);
    ClientPacket equipment(Player owner, boolean hidden);
}
