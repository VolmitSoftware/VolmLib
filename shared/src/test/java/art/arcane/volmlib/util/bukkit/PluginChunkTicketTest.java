package art.arcane.volmlib.util.bukkit;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginChunkTicketTest {
    @Test
    public void overlappingOwnersKeepTheTicketUntilBothRelease() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        when(world.isChunkLoaded(2, 3)).thenReturn(true);
        when(world.addPluginChunkTicket(2, 3, plugin)).thenReturn(true);
        Location location = new Location(world, 32, 64, 48);
        PluginChunkTicket first = PluginChunkTicket.acquire(plugin, location);
        PluginChunkTicket second = PluginChunkTicket.acquire(plugin, location);
        verify(world, times(1)).addPluginChunkTicket(2, 3, plugin);
        first.close();
        first.close();
        verify(world, never()).removePluginChunkTicket(2, 3, plugin);
        second.close();
        verify(world, times(1)).removePluginChunkTicket(2, 3, plugin);
    }

    @Test
    public void anExistingUnmanagedTicketIsNeverRemoved() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        when(world.isChunkLoaded(2, 3)).thenReturn(true);
        try (PluginChunkTicket ignored = PluginChunkTicket.acquire(plugin, new Location(world, 32, 64, 48))) {
            verify(world).addPluginChunkTicket(2, 3, plugin);
        }
        verify(world, never()).removePluginChunkTicket(2, 3, plugin);
    }

    @Test
    public void acquisitionCannotSynchronouslyLoadAnUnloadedChunk() {
        Plugin plugin = mock(Plugin.class);
        World world = mock(World.class);
        assertThrows(IllegalStateException.class,
                () -> PluginChunkTicket.acquire(plugin, new Location(world, 32, 64, 48)));
        verify(world, never()).addPluginChunkTicket(2, 3, plugin);
    }
}
