package art.arcane.volmlib.util;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class VolmLog {
    private static final Logger LOGGER = Logger.getLogger("VolmLib");

    private VolmLog() {
    }

    public static void info(String component, String message) {
        LOGGER.info(format(component, message));
    }

    public static void fine(String component, String message, Throwable failure) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, format(component, message), failure);
        }
    }

    public static void warning(String component, String message) {
        LOGGER.warning(format(component, message));
    }

    public static void warning(String component, String message, Throwable failure) {
        LOGGER.log(Level.WARNING, format(component, message), failure);
    }

    public static void severe(String component, String message, Throwable failure) {
        LOGGER.log(Level.SEVERE, format(component, message), failure);
    }

    private static String format(String component, String message) {
        return "[VolmLib/" + component + "] " + message;
    }
}
