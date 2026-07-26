package art.arcane.volmlib.util.bukkit.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class VolmitPlaceholderExpansion extends PlaceholderExpansion {
    private static final int MAX_LOGGED_FAILURES = 64;

    private final String identifier;
    private final String author;
    private final String version;
    private final String requiredPlugin;
    private final PlaceholderKeyRegistry registry;
    private final Logger logger;
    private final Set<String> loggedFailures = ConcurrentHashMap.newKeySet();

    protected VolmitPlaceholderExpansion(String identifier, String author, String version, String requiredPlugin, PlaceholderKeyRegistry registry, Logger logger) {
        this.identifier = requireValidIdentifier(identifier);
        this.author = Objects.requireNonNull(author, "author");
        this.version = Objects.requireNonNull(version, "version");
        this.requiredPlugin = Objects.requireNonNull(requiredPlugin, "requiredPlugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.logger = Objects.requireNonNull(logger, "logger");

        if (!registry.containsKey(PlaceholderKeyRegistry.AVAILABLE)) {
            throw new IllegalArgumentException("expansion " + this.identifier + " must publish the reserved key '" + PlaceholderKeyRegistry.AVAILABLE + "'");
        }
    }

    @Override
    public final String onRequest(OfflinePlayer player, String params) {
        if (params == null || params.isBlank()) {
            return null;
        }

        String path = params.toLowerCase(Locale.ROOT);

        try {
            return registry.resolve(player == null ? null : player.getUniqueId(), path);
        } catch (Throwable failure) {
            if (loggedFailures.size() < MAX_LOGGED_FAILURES && loggedFailures.add(path)) {
                try {
                    logger.log(Level.WARNING, "placeholder %" + identifier + "_" + path + "% failed and returned " + PlaceholderValues.UNAVAILABLE, failure);
                } catch (Throwable ignored) {
                }
            }

            return PlaceholderValues.UNAVAILABLE;
        }
    }

    @Override
    public final String getIdentifier() {
        return identifier;
    }

    @Override
    public final String getAuthor() {
        return author;
    }

    @Override
    public final String getVersion() {
        return version;
    }

    @Override
    public final String getRequiredPlugin() {
        return requiredPlugin;
    }

    @Override
    public final List<String> getPlaceholders() {
        return registry.keys();
    }

    @Override
    public final boolean persist() {
        return true;
    }

    private static String requireValidIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");

        if (identifier.isEmpty()) {
            throw new IllegalArgumentException("expansion identifier is empty");
        }

        for (int index = 0; index < identifier.length(); index++) {
            char character = identifier.charAt(index);
            boolean letter = character >= 'a' && character <= 'z';
            boolean digit = character >= '0' && character <= '9';

            if (!letter && !digit) {
                throw new IllegalArgumentException("expansion identifier must match [a-z0-9]: " + identifier);
            }
        }

        return identifier;
    }
}
