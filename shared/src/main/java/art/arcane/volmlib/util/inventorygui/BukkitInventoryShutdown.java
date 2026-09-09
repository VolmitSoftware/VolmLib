package art.arcane.volmlib.util.inventorygui;

import art.arcane.volmlib.util.bukkit.BukkitInventoryViews;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class BukkitInventoryShutdown {
    private static final long STOPPING_TIMEOUT_SECONDS = 5L;
    private static final Method SERVER_IS_STOPPING = resolveServerIsStopping();

    private BukkitInventoryShutdown() {
    }

    public static void drain(Plugin plugin, Collection<View> views) {
        Objects.requireNonNull(plugin, "plugin");
        List<View> snapshot = List.copyOf(views);
        DrainContext context = new DrainContext(plugin, new CountDownLatch(snapshot.size()));
        for (View view : snapshot) {
            CloseTask task = new CloseTask(context, view);
            try {
                if (FoliaScheduler.isOwnedByCurrentRegion(view.player())) {
                    task.run();
                } else if (!FoliaScheduler.runEntity(plugin, view.player(), task, 0L, task::retire)) {
                    task.retire();
                }
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.SEVERE, "Could not schedule inventory cleanup during plugin shutdown.", failure);
                task.retire();
            }
        }
        awaitDrain(context);
    }

    private static void awaitDrain(DrainContext context) {
        if (context.drained().getCount() == 0L) {
            return;
        }
        if (serverStopping(context.plugin())) {
            awaitStoppingDrain(context);
            return;
        }
        InterruptedException interruption = null;
        while (context.drained().getCount() > 0L) {
            try {
                context.drained().await();
            } catch (InterruptedException failure) {
                if (interruption == null) {
                    interruption = failure;
                }
            }
        }
        if (interruption != null) {
            Thread.currentThread().interrupt();
            context.plugin().getLogger().log(Level.WARNING,
                    "Inventory shutdown was interrupted while awaiting safe owner cleanup.", interruption);
        }
    }

    private static void awaitStoppingDrain(DrainContext context) {
        try {
            if (!context.drained().await(STOPPING_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                context.plugin().getLogger().warning("Timed out while draining " + context.drained().getCount()
                        + " inventory owner task(s) while the server was stopping.");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            context.plugin().getLogger().log(Level.WARNING,
                    "Inventory shutdown was interrupted while the server was stopping with "
                            + context.drained().getCount() + " owner task(s) still pending.", failure);
        }
    }

    private static boolean serverStopping(Plugin plugin) {
        if (SERVER_IS_STOPPING == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(SERVER_IS_STOPPING.invoke(plugin.getServer()));
        } catch (IllegalAccessException | InvocationTargetException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not determine whether the server is stopping.", failure);
            return false;
        }
    }

    private static Method resolveServerIsStopping() {
        try {
            return Server.class.getMethod("isStopping");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    public record View(Player player, Inventory inventory) {
        public View {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(inventory, "inventory");
        }
    }

    private record DrainContext(Plugin plugin, CountDownLatch drained) {
    }

    private static final class CloseTask implements Runnable {
        private final DrainContext context;
        private final View view;
        private final AtomicBoolean completed = new AtomicBoolean();

        private CloseTask(DrainContext context, View view) {
            this.context = context;
            this.view = view;
        }

        @Override
        public void run() {
            try {
                if (BukkitInventoryViews.top(view.player().getOpenInventory()) == view.inventory()) {
                    view.player().closeInventory();
                }
            } catch (RuntimeException | LinkageError failure) {
                context.plugin().getLogger().log(Level.SEVERE, "Could not close an inventory during plugin shutdown.", failure);
            } finally {
                retire();
            }
        }

        private void retire() {
            if (completed.compareAndSet(false, true)) {
                context.drained().countDown();
            }
        }
    }
}
