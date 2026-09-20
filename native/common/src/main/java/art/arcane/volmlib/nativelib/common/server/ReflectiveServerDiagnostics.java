package art.arcane.volmlib.nativelib.common.server;

import art.arcane.volmlib.nativelib.server.NativeServerDiagnostics;

public class ReflectiveServerDiagnostics implements NativeServerDiagnostics {
    public boolean isCanvas(ClassLoader loader) {
        try {
            Class.forName("io.canvasmc.canvas.region.WorldRegionizer", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }

    public boolean isRaidPersistenceMessage(String message) {
        return message != null && message.contains("Could not save data net.minecraft.world.entity.raid.PersistentRaid");
    }

    public boolean isUnsupportedWorldCreation(Throwable failure) {
        return isRejectedWorldCreation(failure, false);
    }

    public boolean isRejectedWorldCreation(Throwable failure) {
        return isRejectedWorldCreation(failure, true);
    }

    private boolean isRejectedWorldCreation(Throwable failure, boolean includeIllegalState) {
        Throwable cursor = failure;
        while (cursor != null) {
            if (cursor instanceof UnsupportedOperationException || includeIllegalState && cursor instanceof IllegalStateException) {
                for (StackTraceElement element : cursor.getStackTrace()) {
                    if ("org.bukkit.craftbukkit.CraftServer".equals(element.getClassName())
                            && "createWorld".equals(element.getMethodName())) {
                        return true;
                    }
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
