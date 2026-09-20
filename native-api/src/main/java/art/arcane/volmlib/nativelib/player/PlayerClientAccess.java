package art.arcane.volmlib.nativelib.player;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.entity.Player;

@NativeBinding("player.PlayerClientAccessImpl")
public interface PlayerClientAccess {
    static boolean matchesServerPlayer(Player player) {
        return player != null && PlayerClassCache.matches(player.getClass());
    }

    boolean isServerPlayerClass(Class<?> playerClass);
    boolean sendVerticalMotion(Player player, double verticalVelocity);
    ClientBlockTags createBlockTags();
}
