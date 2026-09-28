package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class UIWindowSlotUpdateTest {
    @Test
    public void clearingAControlRestoresItsDecoratedSlot() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element control = mock(Element.class);
        Element background = mock(Element.class);
        ItemStack backgroundItem = mock(ItemStack.class);
        WindowDecorator decorator = mock(WindowDecorator.class);
        when(decorator.onDecorateBackground(fixture.window(), 0, 0)).thenReturn(background);
        when(background.computeItemStack()).thenReturn(backgroundItem);
        fixture.window().setDecorator(decorator);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 0, control);
            fixture.window().updateElement(0, 0, null);
        }

        assertNull(fixture.window().getElement(0, 0));
        verify(fixture.inventory()).setItem(4, backgroundItem);
        verify(fixture.inventory(), never()).getContents();
        unregister(fixture);
    }

    @Test
    public void changesOnlyTheRequestedSlotWithoutReopening() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        ItemStack item = mock(ItemStack.class);
        when(element.computeItemStack()).thenReturn(item);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 1, element);
        }

        verify(fixture.inventory()).getItem(13);
        verify(fixture.inventory()).setItem(13, item);
        verify(fixture.inventory(), times(1)).setItem(anyInt(), any());
        verify(fixture.inventory(), never()).getContents();
        verify(fixture.player(), never()).openInventory(any(Inventory.class));
        verify(fixture.player(), never()).closeInventory();
        verify(element, times(1)).computeItemStack();
        assertSame(element, fixture.window().getElement(0, 1));
        unregister(fixture);
    }

    @Test
    public void unchangedSlotDoesNotSendAnInventoryWrite() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        ItemStack item = mock(ItemStack.class);
        when(element.computeItemStack()).thenReturn(item);
        when(fixture.inventory().getItem(4)).thenReturn(item);

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 0, element);
        }

        verify(fixture.inventory(), never()).setItem(anyInt(), any());
        unregister(fixture);
    }

    @Test
    public void replacedInventoryRejectsTheUpdate() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        when(fixture.view().getTopInventory()).thenReturn(mock(Inventory.class));

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 0, element);
        }

        verify(element, never()).computeItemStack();
        verify(fixture.inventory(), never()).setItem(anyInt(), any());
        unregister(fixture);
    }

    @Test
    public void replacementWindowRejectsQueuedUpdateEvenWhenInventoryWasReused() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        activeWindows().put(fixture.player().getUniqueId(), new UIWindow(fixture.plugin(), fixture.player()));

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 0, element);
        }

        verify(element, never()).computeItemStack();
        verify(fixture.inventory(), never()).setItem(anyInt(), any());
        unregister(fixture);
    }

    @Test
    public void offOwnerUpdateWaitsForTheViewingPlayer() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        ItemStack item = mock(ItemStack.class);
        when(element.computeItemStack()).thenReturn(item);
        when(fixture.plugin().isEnabled()).thenReturn(true);
        AtomicReference<Runnable> task = new AtomicReference<>();

        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(false);
            scheduler.when(() -> FoliaScheduler.runEntity(eq(fixture.plugin()), eq((Entity) fixture.player()),
                any(Runnable.class), eq(1L), eq(null))).thenAnswer(invocation -> {
                    task.set(invocation.getArgument(2));
                    return true;
                });
            fixture.window().updateElement(0, 0, element);
            verify(fixture.player(), never()).getOpenInventory();
            verify(fixture.inventory(), never()).setItem(anyInt(), any());
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            task.get().run();
        }

        verify(fixture.inventory()).setItem(4, item);
        unregister(fixture);
    }

    @Test
    public void offscreenUpdatesKeepTheModelWithoutRendering() throws ReflectiveOperationException {
        Fixture fixture = fixture();
        Element element = mock(Element.class);
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion((Entity) fixture.player())).thenReturn(true);
            fixture.window().updateElement(0, 5, element);
        }

        assertSame(element, fixture.window().getElement(0, 5));
        verify(element, never()).computeItemStack();
        verify(fixture.inventory(), never()).setItem(anyInt(), any());
        unregister(fixture);
    }

    private static Fixture fixture() throws ReflectiveOperationException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Player player = mock(Player.class, withSettings().extraInterfaces(HumanEntity.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Inventory inventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        when(player.getOpenInventory()).thenReturn(view);
        when(inventory.getSize()).thenReturn(27);
        UIWindow window = new UIWindow(plugin, player);
        setField(window, "visible", true);
        setField(window, "inventory", inventory);
        activeWindows().put(player.getUniqueId(), window);
        return new Fixture(plugin, player, inventory, view, window);
    }

    private static void unregister(Fixture fixture) throws ReflectiveOperationException {
        activeWindows().remove(fixture.player().getUniqueId());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, UIWindow> activeWindows() throws ReflectiveOperationException {
        Field field = UIWindow.class.getDeclaredField("ACTIVE_WINDOWS");
        field.setAccessible(true);
        return (Map<UUID, UIWindow>) field.get(null);
    }

    private static void setField(UIWindow window, String name, Object value) throws ReflectiveOperationException {
        Field field = UIWindow.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(window, value);
    }

    private record Fixture(JavaPlugin plugin, Player player, Inventory inventory, InventoryView view, UIWindow window) {
    }
}
