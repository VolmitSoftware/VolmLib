package art.arcane.volmlib.util.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtectionProbeTest {
    @Test
    public void blockProbePreservesProtectionContextAndHandlerList() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        Block block = mock(Block.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInOffHand()).thenReturn(item);

        PlayerInteractEvent event = ProtectionProbe.blockInteract(player, block, EquipmentSlot.OFF_HAND);

        assertTrue(ProtectionProbe.isProbe(event));
        assertSame(PlayerInteractEvent.getHandlerList(), event.getHandlers());
        assertSame(player, event.getPlayer());
        assertSame(block, event.getClickedBlock());
        assertSame(item, event.getItem());
        assertEquals(EquipmentSlot.OFF_HAND, event.getHand());
        assertEquals(Action.RIGHT_CLICK_BLOCK, event.getAction());
        event.setUseInteractedBlock(Event.Result.DENY);
        assertTrue(event.isCancelled());
    }

    @Test
    public void entityProbePreservesProtectionContextAndHandlerList() {
        Player player = mock(Player.class);
        Entity entity = mock(Entity.class);

        PlayerInteractEntityEvent event = ProtectionProbe.entityInteract(player, entity);

        assertTrue(ProtectionProbe.isProbe(event));
        assertSame(PlayerInteractEntityEvent.getHandlerList(), event.getHandlers());
        assertSame(player, event.getPlayer());
        assertSame(entity, event.getRightClicked());
        assertEquals(EquipmentSlot.HAND, event.getHand());
        assertFalse(ProtectionProbe.isProbe(new PlayerInteractEntityEvent(player, entity)));
    }

    @Test
    public void independentLibraryCopiesRecognizeEachOthersProbes() throws Exception {
        ClassLoader loader = new ProbeClassLoader(ProtectionProbe.class.getClassLoader());
        Class<?> otherCopy = loader.loadClass(ProtectionProbe.class.getName());
        Player player = mock(Player.class);
        Entity entity = mock(Entity.class);
        PlayerInteractEntityEvent foreignEvent = (PlayerInteractEntityEvent) otherCopy
            .getMethod("entityInteract", Player.class, Entity.class).invoke(null, player, entity);
        PlayerInteractEntityEvent localEvent = ProtectionProbe.entityInteract(player, entity);

        assertNotSame(ProtectionProbe.class, otherCopy);
        assertTrue(ProtectionProbe.isProbe(foreignEvent));
        assertEquals(Boolean.TRUE, otherCopy.getMethod("isProbe", Event.class).invoke(null, localEvent));
        assertSame(PlayerInteractEntityEvent.getHandlerList(), foreignEvent.getHandlers());
    }

    private static final class ProbeClassLoader extends ClassLoader {
        private ProbeClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(ProtectionProbe.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = loadProbeClass(name);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private Class<?> loadProbeClass(String name) throws ClassNotFoundException {
            try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (stream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = stream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }
}
