package art.arcane.volmlib.nativelib.player;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public record RespawnPoint(World world, int x, int y, int z, float yaw, float pitch) {
    public RespawnPoint {
        Objects.requireNonNull(world, "world");
    }

    public Location location() {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
