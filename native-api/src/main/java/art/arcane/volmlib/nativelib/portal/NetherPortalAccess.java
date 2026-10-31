package art.arcane.volmlib.nativelib.portal;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@NativeBinding("portal.NetherPortalAccessImpl")
public interface NetherPortalAccess {
    CompletableFuture<Void> loadSearch(Location target, int radius);

    List<Location> find(Location target, int radius);

    Optional<Rectangle> inspect(Location portal);

    Optional<Rectangle> create(Creation creation);

    Location destination(Player player, Location origin, Rectangle portal);

    boolean clearDestination(Player player, Location destination);

    Vector movement(Player player);

    record Rectangle(World world, int x, int y, int z, Axis axis, int width, int height) {
        public Rectangle {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(axis, "axis");
            if (axis == Axis.Y || width < 1 || width > 21 || height < 1 || height > 21) {
                throw new IllegalArgumentException("Invalid Nether portal rectangle");
            }
        }
    }

    record Creation(Player player, Location origin, Location target, int radius) {
        public Creation {
            Objects.requireNonNull(player, "player");
            origin = Objects.requireNonNull(origin, "origin").clone();
            target = Objects.requireNonNull(target, "target").clone();
            Objects.requireNonNull(origin.getWorld(), "origin world");
            Objects.requireNonNull(target.getWorld(), "target world");
            origin.checkFinite();
            target.checkFinite();
            if (radius < 0 || radius > 128) {
                throw new IllegalArgumentException("Portal creation radius must be between 0 and 128");
            }
        }

        @Override
        public Location origin() {
            return origin.clone();
        }

        @Override
        public Location target() {
            return target.clone();
        }
    }
}
