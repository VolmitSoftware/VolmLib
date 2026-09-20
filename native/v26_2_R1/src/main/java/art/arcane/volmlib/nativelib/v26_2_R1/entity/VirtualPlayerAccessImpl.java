package art.arcane.volmlib.nativelib.v26_2_R1.entity;

import art.arcane.volmlib.nativelib.common.entity.VirtualPlayersImpl;
import art.arcane.volmlib.nativelib.entity.VirtualPlayerAccess;
import art.arcane.volmlib.nativelib.entity.VirtualPlayers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.function.BiConsumer;

public final class VirtualPlayerAccessImpl implements VirtualPlayerAccess {
    @Override
    public VirtualPlayers create(Plugin plugin, BiConsumer<Player, Runnable> viewerExecutor) {
        return VirtualPlayersImpl.create(plugin, viewerExecutor);
    }
}
