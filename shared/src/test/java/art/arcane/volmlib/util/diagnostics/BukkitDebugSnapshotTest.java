package art.arcane.volmlib.util.diagnostics;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class BukkitDebugSnapshotTest {
    @Test
    public void captureRetainsOnlyImmutableMetadataAndDoesNotReadPlayerIdentity() throws Exception {
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Plugin plugin = mock(Plugin.class);
        CommandSender sender = mock(CommandSender.class);
        World world = mock(World.class);
        List<World> worlds = new ArrayList<>(List.of(world));
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorlds()).thenReturn(worlds);
        when(server.getName()).thenReturn("Paper");
        when(server.getVersion()).thenReturn("Paper 1.21.11");
        when(server.getBukkitVersion()).thenReturn("1.21.11-R0.1-SNAPSHOT");
        when(server.getDefaultGameMode()).thenReturn(GameMode.SURVIVAL);
        when(manager.getPlugins()).thenReturn(new Plugin[]{plugin});
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getName()).thenReturn("Example");
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile(
                new StringReader("name: Example\nversion: 1.0.0\nmain: example.Plugin\n")));
        when(plugin.getDataFolder()).thenReturn(new File("plugin-data"));
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);

        BukkitDebugSnapshot snapshot = BukkitDebugSnapshot.capture(plugin, sender);
        worlds.clear();

        assertEquals("Example", snapshot.pluginName());
        assertEquals("1.0.0", snapshot.pluginVersion());
        assertEquals("1.21.11", snapshot.server().minecraftVersion());
        assertEquals(1, snapshot.server().loadedWorlds());
        assertEquals(Integer.valueOf(1), snapshot.server().worldEnvironments().get("NORMAL"));
        assertFalse(snapshot.plugins().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.plugins().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.server().worldEnvironments().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.plugins().get(0).dependencies().clear());
        verifyNoInteractions(sender);
    }
}
