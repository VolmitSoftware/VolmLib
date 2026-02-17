package art.arcane.volmlib.util.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class Placeholders {
    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";

    private static volatile Method placeholderSetter;
    private static volatile boolean lookupDone;

    private Placeholders() {
    }

    public static String setPlaceholders(Player player, String text) {
        if (text == null || player == null) {
            return text;
        }

        Method method = resolveSetterMethod();
        if (method == null) {
            return text;
        }

        try {
            Object resolved = method.invoke(null, player, text);
            if (resolved instanceof String stringValue) {
                return stringValue;
            }
        } catch (Throwable throwable) {
            return text;
        }

        return text;
    }

    private static Method resolveSetterMethod() {
        if (lookupDone) {
            return placeholderSetter;
        }

        synchronized (Placeholders.class) {
            if (lookupDone) {
                return placeholderSetter;
            }

            lookupDone = true;
            if (Bukkit.getPluginManager().getPlugin(PLACEHOLDER_API_PLUGIN) == null) {
                placeholderSetter = null;
                return null;
            }

            try {
                Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                placeholderSetter = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            } catch (Throwable throwable) {
                placeholderSetter = null;
            }

            return placeholderSetter;
        }
    }
}
