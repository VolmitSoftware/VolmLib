package art.arcane.volmlib.nativelib.common.inventory;

import art.arcane.volmlib.nativelib.inventory.InventoryExtractionAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

public class NativeInventoryExtraction implements InventoryExtractionAccess {
    @Override
    public Inventory filterExtraction(Inventory source, IntPredicate allowedSlot) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(allowedSlot, "allowedSlot");
        if (!(source instanceof CraftInventory inventory)) {
            throw new IllegalArgumentException("Extraction filtering requires a native inventory");
        }
        return new FilteredInventory(inventory, allowedSlot);
    }

    private static final class FilteredInventory extends CraftInventory {
        private final CraftInventory source;

        private FilteredInventory(CraftInventory source, IntPredicate allowedSlot) {
            super(new FilteredContainer(source.getInventory(), allowedSlot));
            this.source = source;
        }

        @Override
        public InventoryType getType() { return source.getType(); }
    }

    private static final class FilteredContainer implements WorldlyContainer {
        private final Container source;
        private final IntPredicate allowedSlot;
        private final int[] slots;

        private FilteredContainer(Container source, IntPredicate allowedSlot) {
            this.source = source;
            this.allowedSlot = allowedSlot;
            slots = new int[source.getContainerSize()];
            for (int slot = 0; slot < slots.length; slot++) {
                slots[slot] = slot;
            }
        }

        @Override
        public int[] getSlotsForFace(Direction direction) {
            return source instanceof WorldlyContainer sided ? sided.getSlotsForFace(direction) : slots;
        }

        @Override
        public boolean canTakeItem(Container destination, int slot, ItemStack stack) {
            return allowedSlot.test(slot) && source.canTakeItem(destination, slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
            return allowedSlot.test(slot) && (!(source instanceof WorldlyContainer sided)
                    || sided.canTakeItemThroughFace(slot, stack, direction));
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
            return !(source instanceof WorldlyContainer sided) || sided.canPlaceItemThroughFace(slot, stack, direction);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) { return source.canPlaceItem(slot, stack); }

        @Override
        public int getContainerSize() { return source.getContainerSize(); }

        @Override
        public boolean isEmpty() { return source.isEmpty(); }

        @Override
        public ItemStack getItem(int slot) { return source.getItem(slot); }

        @Override
        public ItemStack removeItem(int slot, int amount) { return source.removeItem(slot, amount); }

        @Override
        public ItemStack removeItemNoUpdate(int slot) { return source.removeItemNoUpdate(slot); }

        @Override
        public void setItem(int slot, ItemStack stack) { source.setItem(slot, stack); }

        @Override
        public int getMaxStackSize() { return source.getMaxStackSize(); }

        @Override
        public int getMaxStackSize(ItemStack stack) { return source.getMaxStackSize(stack); }

        @Override
        public void setMaxStackSize(int size) { source.setMaxStackSize(size); }

        @Override
        public void setChanged() { source.setChanged(); }

        @Override
        public boolean stillValid(Player player) { return source.stillValid(player); }

        @Override
        public void startOpen(ContainerUser user) { source.startOpen(user); }

        @Override
        public void stopOpen(ContainerUser user) { source.stopOpen(user); }

        @Override
        public List<ContainerUser> getEntitiesWithContainerOpen() { return source.getEntitiesWithContainerOpen(); }

        @Override
        public void clearContent() { source.clearContent(); }

        @Override
        public List<ItemStack> getContents() { return source.getContents(); }

        @Override
        public void onOpen(CraftHumanEntity player) { source.onOpen(player); }

        @Override
        public void onClose(CraftHumanEntity player) { source.onClose(player); }

        @Override
        public List<HumanEntity> getViewers() { return source.getViewers(); }

        @Override
        public InventoryHolder getOwner() { return source.getOwner(); }

        @Override
        public Location getLocation() { return source.getLocation(); }
    }
}
