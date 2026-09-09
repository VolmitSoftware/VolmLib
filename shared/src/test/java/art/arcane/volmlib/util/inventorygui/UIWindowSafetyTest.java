package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class UIWindowSafetyTest {
    @Test
    public void dragTouchingTopInventoryIsCancelled() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        UIWindow window = visibleWindow(plugin, player, inventory);

        when(inventory.getSize()).thenReturn(54);
        when(view.getTopInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn((HumanEntity) player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(8, 54));

        window.on(event);

        verify(event).setCancelled(true);
    }

    @Test
    public void dragRestrictedToBottomInventoryIsNotCancelled() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        UIWindow window = visibleWindow(plugin, player, inventory);

        when(inventory.getSize()).thenReturn(54);
        when(view.getTopInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn((HumanEntity) player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(Set.of(54, 80));

        window.on(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    public void offOwnerCloseRunsOnEntityScheduler() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        UIWindow window = visibleWindow(plugin, player, inventory);
        AtomicReference<Runnable> ownerTask = new AtomicReference<>();
        Entity entity = (Entity) player;

        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(
                    eq(plugin),
                    eq(entity),
                    any(Runnable.class),
                    eq(0L),
                    any(Runnable.class)
            )).thenAnswer(invocation -> {
                ownerTask.set(invocation.getArgument(2));
                return true;
            });

            window.close();

            assertTrue(window.isVisible());
            verify(player, never()).getOpenInventory();
            ownerTask.get().run();
        }

        assertFalse(window.isVisible());
        verify(player).getOpenInventory();
        verify(player).closeInventory();
    }

    @Test
    public void retiredCloseReleasesWindowWithoutPlayerAccess() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        Inventory inventory = mock(Inventory.class);
        UIWindow window = visibleWindow(plugin, player, inventory);
        AtomicReference<Runnable> retiredTask = new AtomicReference<>();
        Entity entity = (Entity) player;

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(
                    eq(plugin),
                    eq(entity),
                    any(Runnable.class),
                    eq(0L),
                    any(Runnable.class)
            )).thenAnswer(invocation -> {
                retiredTask.set(invocation.getArgument(4));
                return true;
            });

            window.close();
            retiredTask.get().run();
        }

        assertFalse(window.isVisible());
        verify(player, never()).getOpenInventory();
        verify(player, never()).closeInventory();
    }

    @Test
    public void rejectedCloseScheduleRunsRetiredCleanup() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        Inventory inventory = mock(Inventory.class);
        UIWindow window = visibleWindow(plugin, player, inventory);
        Entity entity = (Entity) player;

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(
                    eq(plugin),
                    eq(entity),
                    any(Runnable.class),
                    eq(0L),
                    any(Runnable.class)
            )).thenReturn(false);

            window.close();
        }

        assertFalse(window.isVisible());
        verify(player, never()).getOpenInventory();
        verify(player, never()).closeInventory();
    }

    @Test
    public void rejectedClickScheduleNeverFallsBackToGlobalOwnership() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = player();
        UIWindow window = new UIWindow(plugin, player);
        Runnable click = mock(Runnable.class);
        Runnable retired = mock(Runnable.class);
        Entity entity = (Entity) player;

        when(plugin.isEnabled()).thenReturn(true);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(
                    plugin,
                    entity,
                    click,
                    1L,
                    retired
            )).thenReturn(false);

            assertFalse(invokeQueueSync(window, click, retired));

            scheduler.verify(() -> FoliaScheduler.runGlobal(plugin, click, 1L), never());
        }

        verify(click, never()).run();
    }

    private static Player player() {
        Player player = mock(Player.class, withSettings().extraInterfaces(HumanEntity.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static UIWindow visibleWindow(JavaPlugin plugin, Player player, Inventory inventory)
            throws ReflectiveOperationException {
        UIWindow window = new UIWindow(plugin, player);
        setField(window, "visible", true);
        setField(window, "inventory", inventory);
        return window;
    }

    private static void setField(UIWindow window, String name, Object value) throws ReflectiveOperationException {
        Field field = UIWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(window, value);
    }

    private static boolean invokeQueueSync(UIWindow window, Runnable runnable, Runnable retired)
            throws ReflectiveOperationException {
        Method method = UIWindow.class.getDeclaredMethod("queueSync", Runnable.class, Runnable.class);
        method.setAccessible(true);
        return (boolean) method.invoke(window, runnable, retired);
    }
}
