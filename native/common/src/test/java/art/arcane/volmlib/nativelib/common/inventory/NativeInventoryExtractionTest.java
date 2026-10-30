package art.arcane.volmlib.nativelib.common.inventory;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeInventoryExtractionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup())
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    @Test
    void nativeEligibilitySkipsFuelAndKeepsLaterOutputEligible() throws Exception {
        SimpleContainer source = new SimpleContainer(3);
        source.setItem(0, new ItemStack(Items.COAL, 64));
        source.setItem(1, new ItemStack(Items.DIAMOND, 3));
        CraftInventory inventory = new CraftInventory(source);
        Container filtered = ((CraftInventory) new NativeInventoryExtraction()
                .filterExtraction(inventory, slot -> !source.getItem(slot).is(Items.COAL))).getInventory();
        Method eligible = HopperBlockEntity.class.getDeclaredMethod("canTakeItemFromContainer",
                Container.class, Container.class, ItemStack.class, int.class, Direction.class);
        eligible.setAccessible(true);
        SimpleContainer destination = new SimpleContainer(5);

        assertFalse((boolean) eligible.invoke(null, destination, filtered, filtered.getItem(0), 0, Direction.DOWN));
        assertTrue((boolean) eligible.invoke(null, destination, filtered, filtered.getItem(1), 1, Direction.DOWN));
        assertEquals(64, source.getItem(0).getCount());
        assertEquals(3, source.getItem(1).getCount());
        source.setItem(1, new ItemStack(Items.COAL, 4));
        assertFalse((boolean) eligible.invoke(null, destination, filtered, filtered.getItem(1), 1, Direction.DOWN));
    }

    @Test
    void nativeMutationsUseActualStacksAndPreserveMetadata() {
        SimpleContainer source = new SimpleContainer(3);
        ItemStack original = new ItemStack(Items.DIAMOND, 8);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Filtered output"));
        source.setItem(1, original);
        Container filtered = ((CraftInventory) new NativeInventoryExtraction()
                .filterExtraction(new CraftInventory(source), slot -> true)).getInventory();

        assertSame(source.getItem(1), filtered.getItem(1));
        filtered.getItem(1).setCount(1);
        assertEquals(1, source.getItem(1).getCount());
        filtered.getItem(1).setCount(8);
        ItemStack removed = filtered.removeItem(1, 3);
        assertEquals(3, removed.getCount());
        assertEquals(original.get(DataComponents.CUSTOM_NAME), removed.get(DataComponents.CUSTOM_NAME));
        assertEquals(5, source.getItem(1).getCount());
        filtered.setItem(1, removed);
        assertSame(removed, source.getItem(1));
        assertSame(source.getContents(), filtered.getContents());
        assertEquals(3, filtered.removeItemNoUpdate(1).getCount());
        assertTrue(source.isEmpty());
    }

    @Test
    void preservesSidedRestrictionsAndRechecksPolicy() {
        WorldlyContainer source = Mockito.mock(WorldlyContainer.class);
        Container destination = Mockito.mock(Container.class);
        ItemStack stack = new ItemStack(Items.DIAMOND);
        AtomicBoolean allowed = new AtomicBoolean(true);
        Mockito.when(source.getContainerSize()).thenReturn(3);
        Mockito.when(source.getSlotsForFace(Direction.DOWN)).thenReturn(new int[]{1, 2});
        Mockito.when(source.canTakeItem(destination, 1, stack)).thenReturn(true);
        Mockito.when(source.canTakeItemThroughFace(1, stack, Direction.DOWN)).thenReturn(true);
        WorldlyContainer filtered = (WorldlyContainer) ((CraftInventory) new NativeInventoryExtraction()
                .filterExtraction(new CraftInventory(source), slot -> allowed.get())).getInventory();

        assertArrayEquals(new int[]{1, 2}, filtered.getSlotsForFace(Direction.DOWN));
        assertTrue(filtered.canTakeItem(destination, 1, stack));
        assertTrue(filtered.canTakeItemThroughFace(1, stack, Direction.DOWN));
        assertFalse(filtered.canTakeItemThroughFace(1, stack, Direction.UP));
        assertFalse(filtered.canPlaceItemThroughFace(1, stack, Direction.DOWN));
        assertFalse(filtered.canTakeItem(destination, 2, stack));
        allowed.set(false);
        assertFalse(filtered.canTakeItem(destination, 1, stack));
        assertFalse(filtered.canTakeItemThroughFace(1, stack, Direction.DOWN));
    }

    @Test
    void preservesInventoryTypeHolderAndDelegatesDirtyAndCapacityUpdates() {
        Container source = Mockito.mock(Container.class);
        InventoryHolder owner = Mockito.mock(InventoryHolder.class);
        Location location = new Location(Mockito.mock(World.class), 3, 64, 5);
        List<HumanEntity> viewers = List.of(Mockito.mock(HumanEntity.class));
        Mockito.when(source.getContainerSize()).thenReturn(9);
        Mockito.when(source.getOwner()).thenReturn(owner);
        Mockito.when(source.getLocation()).thenReturn(location);
        Mockito.when(source.getViewers()).thenReturn(viewers);
        Mockito.when(source.getMaxStackSize()).thenReturn(16);
        CraftInventory original = Mockito.mock(CraftInventory.class);
        Mockito.when(original.getInventory()).thenReturn(source);
        Mockito.when(original.getType()).thenReturn(InventoryType.DROPPER);
        CraftInventory inventory = (CraftInventory) new NativeInventoryExtraction()
                .filterExtraction(original, slot -> true);
        Container filtered = inventory.getInventory();

        assertSame(owner, inventory.getHolder());
        assertSame(location, inventory.getLocation());
        assertSame(viewers, inventory.getViewers());
        assertEquals(9, inventory.getSize());
        assertEquals(16, inventory.getMaxStackSize());
        assertEquals(InventoryType.DROPPER, inventory.getType());
        filtered.setChanged();
        filtered.setMaxStackSize(32);
        filtered.clearContent();
        Mockito.verify(source).setChanged();
        Mockito.verify(source).setMaxStackSize(32);
        Mockito.verify(source).clearContent();
        assertArrayEquals(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, ((WorldlyContainer) filtered).getSlotsForFace(Direction.DOWN));
    }

    @Test
    void refusesNonNativeInventoriesInsteadOfCreatingDetachedStorage() {
        NativeInventoryExtraction access = new NativeInventoryExtraction();
        assertThrows(IllegalArgumentException.class, () -> access.filterExtraction(Mockito.mock(Inventory.class), slot -> true));
        assertThrows(NullPointerException.class, () -> access.filterExtraction(new CraftInventory(new SimpleContainer(1)), null));
    }
}
