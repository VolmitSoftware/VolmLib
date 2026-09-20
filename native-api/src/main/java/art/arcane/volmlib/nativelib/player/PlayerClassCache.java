package art.arcane.volmlib.nativelib.player;

import art.arcane.volmlib.nativelib.NativeAdapters;
import org.bukkit.Bukkit;

final class PlayerClassCache {
    private static final ClassValue<Boolean> SERVER_PLAYERS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return NativeAdapters.find(PlayerClientAccess.class)
                    .map(access -> access.isServerPlayerClass(type)).orElse(false);
        }
    };

    private PlayerClassCache() {
    }

    static boolean matches(Class<?> playerClass) {
        return Bukkit.getServer() != null && SERVER_PLAYERS.get(playerClass);
    }
}
