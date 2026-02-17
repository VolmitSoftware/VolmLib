package art.arcane.volmlib.util.director.compat;

import art.arcane.volmlib.util.director.context.DirectorThreadContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BukkitDirectorContext {
    private static final DirectorThreadContext<CommandSender> senderContext = new DirectorThreadContext<>();

    private BukkitDirectorContext() {
    }

    public static void touch(CommandSender sender) {
        senderContext.touch(sender);
    }

    public static void remove() {
        senderContext.remove();
    }

    public static CommandSender sender() {
        return senderContext.get();
    }

    public static Player player() {
        CommandSender sender = sender();
        if (sender instanceof Player player) {
            return player;
        }

        return null;
    }

    public static boolean isPlayer() {
        return sender() instanceof Player;
    }

    public static boolean isConsole() {
        return !isPlayer();
    }

    public static boolean hasPermission(String permission) {
        CommandSender sender = sender();
        return sender != null && sender.hasPermission(permission);
    }

    public static String name() {
        CommandSender sender = sender();
        return sender == null ? "unknown" : sender.getName();
    }
}
