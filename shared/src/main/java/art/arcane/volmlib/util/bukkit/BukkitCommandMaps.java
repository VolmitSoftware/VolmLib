package art.arcane.volmlib.util.bukkit;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

public final class BukkitCommandMaps {
    private BukkitCommandMaps() {
    }

    public static CommandMap commandMap(Server server) {
        Server requiredServer = Objects.requireNonNull(server, "server");
        Object result;
        try {
            Method method = requiredServer.getClass().getMethod("getCommandMap");
            result = method.invoke(requiredServer);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Unable to access the server command map", failure);
        }
        if (result instanceof CommandMap commands) {
            return commands;
        }
        throw new IllegalStateException("The server did not provide a command map");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Command> knownCommands(CommandMap commandMap) {
        CommandMap requiredMap = Objects.requireNonNull(commandMap, "commandMap");
        Object result;
        try {
            try {
                Method method = requiredMap.getClass().getMethod("getKnownCommands");
                result = method.invoke(requiredMap);
            } catch (NoSuchMethodException missingAccessor) {
                if (!(requiredMap instanceof SimpleCommandMap)) {
                    throw missingAccessor;
                }
                Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
                field.setAccessible(true);
                result = field.get(requiredMap);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Unable to access registered commands", failure);
        }
        if (result instanceof Map<?, ?>) {
            return (Map<String, Command>) result;
        }
        throw new IllegalStateException("The command map did not provide its registered commands");
    }
}
