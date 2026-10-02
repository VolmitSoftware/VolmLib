package art.arcane.volmlib.nativelib.player;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@NativeBinding("player.PlayerRespawnAccessImpl")
public interface PlayerRespawnAccess {
    RespawnPolicy snapshot(Player player);

    Optional<Location> validate(RespawnPolicy policy);

    CompletableFuture<Location> findSharedSpawn(RespawnPoint sharedSpawn);
}
