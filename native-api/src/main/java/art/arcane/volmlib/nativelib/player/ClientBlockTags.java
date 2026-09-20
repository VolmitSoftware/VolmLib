package art.arcane.volmlib.nativelib.player;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface ClientBlockTags {
    boolean sendClimbingState(Player player, Material suppressedMaterial);
    void invalidate();
}
