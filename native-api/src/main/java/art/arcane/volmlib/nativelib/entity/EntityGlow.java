package art.arcane.volmlib.nativelib.entity;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface EntityGlow {
    void setGlowing(Entity entity, Player receiver, ChatColor color) throws ReflectiveOperationException;
    void unsetGlowing(int entityId, Player receiver) throws ReflectiveOperationException;
    void disable();
}
