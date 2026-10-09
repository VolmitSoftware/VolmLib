package art.arcane.volmlib.util.bukkit;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class BukkitCommandMapsTest {
    @Test
    public void returnsTheServersActualMapAndMutableCommandTable() {
        Server server = mock(Server.class, withSettings().extraInterfaces(CommandServer.class));
        CommandMap map = mock(CommandMap.class, withSettings().extraInterfaces(KnownCommandMap.class));
        Map<String, Command> commands = new HashMap<>();
        when(((CommandServer) server).getCommandMap()).thenReturn(map);
        when(((KnownCommandMap) map).getKnownCommands()).thenReturn(commands);

        assertSame(map, BukkitCommandMaps.commandMap(server));
        assertSame(commands, BukkitCommandMaps.knownCommands(map));
        Command command = mock(Command.class);
        BukkitCommandMaps.knownCommands(map).put("plugin:companions", command);
        assertSame(command, commands.get("plugin:companions"));
    }

    @Test
    public void preservesCommandMapAccessorFailure() {
        Server server = mock(Server.class, withSettings().extraInterfaces(CommandServer.class));
        IllegalArgumentException original = new IllegalArgumentException("server rejected access");
        when(((CommandServer) server).getCommandMap()).thenThrow(original);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BukkitCommandMaps.commandMap(server));
        assertTrue(failure.getCause() instanceof InvocationTargetException);
        assertSame(original, failure.getCause().getCause());
    }

    @Test
    public void preservesKnownCommandsAccessorFailure() {
        CommandMap map = mock(CommandMap.class, withSettings().extraInterfaces(KnownCommandMap.class));
        IllegalArgumentException original = new IllegalArgumentException("table rejected access");
        when(((KnownCommandMap) map).getKnownCommands()).thenThrow(original);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BukkitCommandMaps.knownCommands(map));
        assertTrue(failure.getCause() instanceof InvocationTargetException);
        assertSame(original, failure.getCause().getCause());
    }

    public interface CommandServer {
        CommandMap getCommandMap();
    }

    public interface KnownCommandMap {
        Map<String, Command> getKnownCommands();
    }
}
