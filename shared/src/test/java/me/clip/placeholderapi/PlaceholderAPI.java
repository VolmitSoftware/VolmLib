package me.clip.placeholderapi;

import org.bukkit.OfflinePlayer;

/** Stands in for PlaceholderAPI's static entry point, with the one method VolmLib binds. */
public final class PlaceholderAPI {
    private PlaceholderAPI() {
    }

    public static String setPlaceholders(OfflinePlayer player, String text) {
        return text.replace("%server_online%", "3").replace("%viewer%", player == null ? "nobody" : "someone");
    }
}
