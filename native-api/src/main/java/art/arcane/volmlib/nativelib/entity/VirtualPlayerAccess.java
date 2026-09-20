package art.arcane.volmlib.nativelib.entity;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.function.BiConsumer;

@NativeBinding("entity.VirtualPlayerAccessImpl")
public interface VirtualPlayerAccess {
    VirtualPlayers create(Plugin plugin, BiConsumer<Player, Runnable> viewerExecutor);
}
