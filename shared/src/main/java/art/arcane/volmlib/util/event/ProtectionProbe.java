package art.arcane.volmlib.util.event;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ProtectionProbe {
    private static final String EVENT_NAME = "VolmLibProtectionProbe";

    private ProtectionProbe() {
    }

    public static boolean isProbe(Event event) {
        return EVENT_NAME.equals(event.getEventName());
    }

    public static PlayerInteractEvent blockInteract(Player player, Block block, EquipmentSlot hand) {
        ItemStack item = hand == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();
        return new BlockProbe(player, new BlockInteraction(block, item, hand));
    }

    public static PlayerInteractEntityEvent entityInteract(Player player, Entity entity) {
        return new EntityProbe(player, entity);
    }

    private record BlockInteraction(Block block, ItemStack item, EquipmentSlot hand) {
    }

    private static final class BlockProbe extends PlayerInteractEvent {
        private BlockProbe(Player player, BlockInteraction interaction) {
            super(player, Action.RIGHT_CLICK_BLOCK, interaction.item(), interaction.block(),
                BlockFace.UP, interaction.hand());
        }

        @Override
        public String getEventName() {
            return EVENT_NAME;
        }
    }

    private static final class EntityProbe extends PlayerInteractEntityEvent {
        private EntityProbe(Player player, Entity entity) {
            super(player, entity, EquipmentSlot.HAND);
        }

        @Override
        public String getEventName() {
            return EVENT_NAME;
        }
    }
}
