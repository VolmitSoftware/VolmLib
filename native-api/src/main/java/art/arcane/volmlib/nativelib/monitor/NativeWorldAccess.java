package art.arcane.volmlib.nativelib.monitor;

import art.arcane.volmlib.nativelib.NativeBinding;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

@NativeBinding("monitor.NativeWorldAccessImpl")
public interface NativeWorldAccess {
    EntityRangeSettings entityRanges(World world);

    HopperAccess hopper(World world, int x, int y, int z);

    boolean tickFluid(World world, int x, int y, int z, boolean water, boolean lava);

    boolean setNavigationBudget(Mob mob, float multiplier);

    void resetNavigationBudget(Mob mob);

    void sendCollectPacket(Player player, int entityId, int collectorId, int amount);

    interface HopperAccess {
        boolean addItem(Item item);

        boolean isEmpty();

        int cooldown();

        void cooldown(int ticks);
    }
}
