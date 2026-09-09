package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitInventoryShutdownTest {
    private Plugin plugin;
    private Player player;
    private Entity entity;
    private Inventory inventory;
    private InventoryView open;
    private Logger logger;

    @Before
    public void setUp() {
        plugin = mock(Plugin.class);
        player = mock(Player.class, withSettings().extraInterfaces(Entity.class));
        entity = (Entity) player;
        inventory = mock(Inventory.class);
        open = mock(InventoryView.class);
        logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(mock(Server.class));
        when(plugin.getLogger()).thenReturn(logger);
        when(player.getOpenInventory()).thenReturn(open);
        when(open.getTopInventory()).thenReturn(inventory);
    }

    @Test
    public void ownedInventoryClosesImmediately() {
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(true);
            BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
        }
        verify(player).closeInventory();
    }

    @Test
    public void anotherInventoryOpenedBeforeCleanupIsPreserved() {
        when(open.getTopInventory()).thenReturn(mock(Inventory.class));
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(true);
            BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
        }
        verify(player, never()).closeInventory();
    }

    @Test(timeout = 5000L)
    public void interruptionDoesNotReleaseUnfinishedOwnerCleanup() throws Exception {
        CountDownLatch scheduled = new CountDownLatch(1);
        AtomicReference<Runnable> cleanup = new AtomicReference<>();
        CompletableFuture<Boolean> completed = new CompletableFuture<>();
        Thread closing = new Thread(() -> {
            try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
                scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                        eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                    cleanup.set(invocation.getArgument(2, Runnable.class));
                    scheduled.countDown();
                    return true;
                });
                Thread.currentThread().interrupt();
                BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
                completed.complete(Thread.currentThread().isInterrupted());
            } catch (Throwable failure) {
                completed.completeExceptionally(failure);
            }
        }, "Inventory-Shutdown-Test");
        closing.setDaemon(true);
        closing.start();
        try {
            assertTrue(scheduled.await(2L, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1L);
            while (closing.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.sleep(1L);
            }
            assertEquals(Thread.State.WAITING, closing.getState());
            assertFalse(completed.isDone());
            verify(player, never()).getOpenInventory();
            cleanup.get().run();
            assertTrue(completed.get(2L, TimeUnit.SECONDS));
            verify(player).closeInventory();
        } finally {
            if (cleanup.get() != null && !completed.isDone()) {
                cleanup.get().run();
            }
            closing.join(1000L);
        }
    }

    @Test(timeout = 2000L)
    public void retiredOwnerCompletesWithoutInventoryAccess() {
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runEntity(same(plugin), same(entity), any(Runnable.class),
                    eq(0L), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(4, Runnable.class).run();
                return true;
            });
            BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
        }
        verify(player, never()).getOpenInventory();
        verify(player, never()).closeInventory();
    }

    @Test(timeout = 2000L)
    public void rejectedOwnerDispatchCompletesWithoutInventoryAccess() {
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
        }
        verify(player, never()).getOpenInventory();
        verify(player, never()).closeInventory();
    }

    @Test(timeout = 2000L)
    public void failedCloseLogsItsCauseAndCompletes() {
        IllegalStateException failure = new IllegalStateException("Inventory close failed");
        doThrow(failure).when(player).closeInventory();
        try (MockedStatic<FoliaScheduler> scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.isOwnedByCurrentRegion(entity)).thenReturn(true);
            BukkitInventoryShutdown.drain(plugin, List.of(new BukkitInventoryShutdown.View(player, inventory)));
        }
        verify(logger).log(eq(Level.SEVERE), anyString(), same(failure));
    }
}
