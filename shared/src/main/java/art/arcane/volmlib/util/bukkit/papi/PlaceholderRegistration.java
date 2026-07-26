package art.arcane.volmlib.util.bukkit.papi;

import org.bukkit.Bukkit;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlaceholderRegistration {
    private static final String PLACEHOLDER_API_PLUGIN = "PlaceholderAPI";

    private final Logger logger;
    private volatile VolmitPlaceholderExpansion expansion;

    public PlaceholderRegistration(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public static boolean isPlaceholderApiEnabled() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(PLACEHOLDER_API_PLUGIN);
        } catch (Throwable failure) {
            return false;
        }
    }

    public boolean register(Supplier<VolmitPlaceholderExpansion> factory) {
        Objects.requireNonNull(factory, "factory");

        if (expansion != null) {
            return true;
        }

        if (!isPlaceholderApiEnabled()) {
            return false;
        }

        VolmitPlaceholderExpansion built;

        try {
            built = factory.get();
        } catch (Throwable failure) {
            logger.log(Level.SEVERE, "failed to build the PlaceholderAPI expansion", failure);
            return false;
        }

        if (built == null) {
            return false;
        }

        try {
            if (!built.register()) {
                logger.log(Level.WARNING, "PlaceholderAPI rejected expansion " + built.getIdentifier());
                return false;
            }
        } catch (Throwable failure) {
            logger.log(Level.SEVERE, "failed to register PlaceholderAPI expansion " + built.getIdentifier(), failure);
            return false;
        }

        expansion = built;
        return true;
    }

    public void unregister() {
        VolmitPlaceholderExpansion registered = expansion;
        expansion = null;

        if (registered == null) {
            return;
        }

        try {
            registered.unregister();
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "failed to unregister PlaceholderAPI expansion " + registered.getIdentifier(), failure);
        }
    }

    public boolean isRegistered() {
        return expansion != null;
    }
}
