package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PlaceholderAPI fills reached by reflection, so callers neither depend on PlaceholderAPI nor load its classes when it
 * is absent. PlaceholderAPI runs each expansion on the calling thread.
 */
public final class Placeholders {
    private static final PlaceholderApiBinding PLACEHOLDER_API = new PlaceholderApiBinding(
            Bukkit::getPluginManager,
            () -> JavaPlugin.getProvidingPlugin(Placeholders.class),
            System::currentTimeMillis
    );

    private Placeholders() {
    }

    public static boolean containsPlaceholder(String text) {
        return text != null && text.indexOf('%') >= 0;
    }

    /** The text filled for the player; a null player gets the text as written. */
    public static String setPlaceholders(Player player, String text) {
        return player == null ? text : PLACEHOLDER_API.fill(player, text);
    }

    /**
     * The text filled for no player, so only placeholders that read the server, such as {@code %server_online%},
     * answer. Call on the main thread, or the global region on Folia.
     */
    public static String setServerPlaceholders(String text) {
        return PLACEHOLDER_API.fill(null, text);
    }
}
