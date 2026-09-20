package art.arcane.volmlib.nativelib.entity;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface ClientPacket {
    void send(Player viewer);
}
