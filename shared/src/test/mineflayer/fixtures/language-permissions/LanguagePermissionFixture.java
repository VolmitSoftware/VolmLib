package art.arcane.volmlib.test;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

public final class LanguagePermissionFixture extends JavaPlugin implements Listener {
    private final Map<UUID, PermissionAttachment> editorPermissions = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String mode = player.getName().toLowerCase(Locale.ROOT);
        if (mode.equals("selfallowed")) {
            return;
        }
        PermissionAttachment permissions = player.addAttachment(this);
        switch (mode) {
            case "selfdenied" -> permissions.setPermission("shapedportals.language.self", false);
            case "shareddenied" -> permissions.setPermission("volmit.language.self", false);
            case "serverallowed" -> {
                permissions.setPermission("volmit.language.self", false);
                permissions.setPermission("shapedportals.language.self", false);
                permissions.setPermission("volmit.language.admin", true);
            }
            case "debuggranted", "debugreload" -> {
                permissions.setPermission("shapedportals.debugdump", true);
                permissions.setPermission("biletools.debugdump", true);
                permissions.setPermission("shapedportals.command", false);
                permissions.setPermission("shapedportals.config", false);
                permissions.setPermission("bile.use", mode.equals("debugreload"));
            }
            case "languageadmin" -> {
                permissions.setPermission("volmit.language.admin", true);
                permissions.setPermission("bile.use", true);
                permissions.setPermission("shapedportals.command", true);
            }
            case "editoradmin" -> {
                editorPermissions.put(player.getUniqueId(), permissions);
                setEditorPermissions(permissions, true);
                permissions.setPermission("shapedportals.command", true);
            }
            case "languagedenied", "provideradmins" -> {
                permissions.setPermission("volmit.language.admin", false);
                permissions.setPermission("bile.use", true);
                permissions.setPermission("shapedportals.command", true);
                permissions.setPermission("shapedportals.config", mode.equals("provideradmins"));
            }
            default -> player.removeAttachment(permissions);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editorPermissions.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!(sender instanceof Player player) || arguments.length != 1) {
            return false;
        }
        PermissionAttachment permissions = editorPermissions.get(player.getUniqueId());
        if (permissions == null) {
            return false;
        }
        switch (arguments[0]) {
            case "revoke" -> {
                setEditorPermissions(permissions, false);
                player.sendMessage("Editor permissions revoked.");
            }
            case "restore" -> {
                setEditorPermissions(permissions, true);
                player.sendMessage("Editor permissions restored.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setEditorPermissions(PermissionAttachment permissions, boolean enabled) {
        permissions.setPermission("volmit.language.admin", enabled);
        permissions.setPermission("bile.use", enabled);
        permissions.setPermission("shapedportals.config", enabled);
    }
}
