package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class NativeHarvestSession {
    private final ServerLevel level;
    private final ServerPlayer player;
    private final int heldSlot;
    private final ItemStack toolBefore;
    private ItemStack expectedTool = ItemStack.EMPTY;
    private boolean toolBroken;

    public NativeHarvestSession(NativeWorld world, NativeProtocolPlayer player) {
        level = (ServerLevel) world.nativeHandle();
        this.player = player.player();
        heldSlot = this.player.getInventory().getSelectedSlot();
        toolBefore = this.player.getInventory().getSelectedItem().copy();
    }

    public static boolean survival(NativeProtocolPlayer player) {
        return player.player().gameMode().isSurvival();
    }

    public static boolean sneaking(NativeProtocolPlayer player) {
        return player.player().isShiftKeyDown();
    }

    public static boolean holdingAxe(NativeProtocolPlayer player) {
        return player.player().getInventory().getSelectedItem().is(ItemTags.AXES);
    }

    public static boolean hasPermission(NativeModdedLoader loader, NativeProtocolPlayer player) {
        return loader.hasBlockBreakPermission(player.player());
    }

    public boolean canBreak(NativeModdedLoader loader, NativeBlockPoint point, NativeBlockState state) {
        return loader.canBreakBlock(level, player, position(point), (BlockState) state.nativeHandle());
    }

    public boolean survival() {
        return player.gameMode().isSurvival();
    }

    public boolean sneaking() {
        return player.isShiftKeyDown();
    }

    public boolean holdingAxe() {
        return player.getInventory().getSelectedItem().is(ItemTags.AXES);
    }

    public boolean toolBroken() {
        return toolBroken;
    }

    public boolean active() {
        if (player.isRemoved() || player.hasDisconnected() || player.level() != level
                || player.getInventory().getSelectedSlot() != heldSlot || expectedTool.isEmpty()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(player.getInventory().getSelectedItem(), expectedTool);
    }

    public boolean normalizeOriginTool(boolean preserve) {
        ItemStack current = player.getInventory().getItem(heldSlot);
        boolean matches = !current.isEmpty() && ItemStack.matchesIgnoringComponents(
                toolBefore, current, componentType -> componentType == DataComponents.DAMAGE);
        if (!toolBefore.isDamageableItem()) {
            if (!matches || !ItemStack.isSameItemSameComponents(toolBefore, current)) {
                return false;
            }
            expectedTool = current.copy();
            return true;
        }
        int damage = toolBefore.getDamageValue() + (preserve ? 0 : 1);
        if (damage >= toolBefore.getMaxDamage()) {
            if (!current.isEmpty() && !matches) {
                return false;
            }
            Item brokenItem = toolBefore.getItem();
            player.getInventory().setItem(heldSlot, ItemStack.EMPTY);
            if (!current.isEmpty()) {
                player.onEquippedItemBroken(brokenItem, EquipmentSlot.MAINHAND);
            }
            player.inventoryMenu.sendAllDataToRemote();
            expectedTool = ItemStack.EMPTY;
            toolBroken = true;
            return true;
        }
        if (!current.isEmpty() && !matches) {
            return false;
        }
        ItemStack normalized = toolBefore.copy();
        normalized.setDamageValue(damage);
        player.getInventory().setItem(heldSlot, normalized);
        player.inventoryMenu.sendAllDataToRemote();
        expectedTool = normalized.copy();
        return true;
    }

    public Reservation reserveToolDamage(boolean preserve) {
        ItemStack current = player.getInventory().getSelectedItem();
        if (!ItemStack.isSameItemSameComponents(current, expectedTool) || !current.is(ItemTags.AXES)) {
            return null;
        }
        ItemStack before = current.copy();
        if (!current.isDamageableItem() || preserve) {
            return new Reservation(before, false, false);
        }
        int damage = current.getDamageValue() + 1;
        if (damage >= current.getMaxDamage()) {
            Item brokenItem = current.getItem();
            player.getInventory().setSelectedItem(ItemStack.EMPTY);
            player.onEquippedItemBroken(brokenItem, EquipmentSlot.MAINHAND);
            player.inventoryMenu.sendAllDataToRemote();
            expectedTool = ItemStack.EMPTY;
            return new Reservation(before, true, true);
        }
        current.setDamageValue(damage);
        player.getInventory().setSelectedItem(current);
        player.inventoryMenu.sendAllDataToRemote();
        expectedTool = current.copy();
        return new Reservation(before, true, false);
    }

    public void refundToolDamage(Reservation reservation) {
        if (!reservation.charged) {
            return;
        }
        ItemStack current = player.getInventory().getSelectedItem();
        if (expectedTool.isEmpty() ? current.isEmpty() : ItemStack.isSameItemSameComponents(current, expectedTool)) {
            ItemStack restored = reservation.before.copy();
            player.getInventory().setSelectedItem(restored);
            player.inventoryMenu.sendAllDataToRemote();
            expectedTool = restored.copy();
        }
    }

    public boolean loaded(NativeBlockPoint point) {
        return level.isLoaded(position(point));
    }

    public boolean mayDestroy(NativeBlockPoint point, NativeBlockState state) {
        BlockPos position = position(point);
        return level.mayInteract(player, position) && !player.blockActionRestricted(level, position, player.gameMode())
                && player.getInventory().getSelectedItem().canDestroyBlock((BlockState) state.nativeHandle(), level, position, player);
    }

    public List<NativeItemStack> drops(NativeBlockPoint point, NativeBlockState blockState) {
        BlockPos position = position(point);
        BlockState state = (BlockState) blockState.nativeHandle();
        ItemStack tool = player.getInventory().getSelectedItem().copy();
        BlockEntity entity = state.hasBlockEntity() ? level.getBlockEntity(position) : null;
        List<ItemStack> drops = Block.getDrops(state, level, position, entity, player, tool);
        List<NativeItemStack> items = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            items.add(new NativeItemStack(drop));
        }
        return items;
    }

    public boolean destroy(NativeBlockPoint point) {
        return level.destroyBlock(position(point), false, player, 512);
    }

    public static boolean log(NativeBlockState state) {
        return ((BlockState) state.nativeHandle()).is(BlockTags.LOGS);
    }

    public static boolean air(NativeBlockState state) {
        return ((BlockState) state.nativeHandle()).isAir();
    }

    private static BlockPos position(NativeBlockPoint point) {
        return new BlockPos(point.x(), point.y(), point.z());
    }

    public static final class Reservation {
        private final ItemStack before;
        private final boolean charged;
        private final boolean broke;

        private Reservation(ItemStack before, boolean charged, boolean broke) {
            this.before = before;
            this.charged = charged;
            this.broke = broke;
        }

        public boolean broke() {
            return broke;
        }
    }
}
