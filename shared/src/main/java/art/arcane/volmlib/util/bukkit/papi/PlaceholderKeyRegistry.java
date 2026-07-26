package art.arcane.volmlib.util.bukkit.papi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PlaceholderKeyRegistry {
    public static final String AVAILABLE = "available";

    private static final int MAX_SEGMENTS = 4;
    private static final String GROUP_SUFFIX = ".*";

    private final Map<String, Resolver> keys;
    private final Map<String, GroupResolver> groups;
    private final List<String> published;

    private PlaceholderKeyRegistry(Map<String, Resolver> keys, Map<String, GroupResolver> groups) {
        this.keys = Map.copyOf(keys);
        this.groups = Map.copyOf(groups);
        this.published = publish(this.keys, this.groups);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String resolve(UUID playerId, String path) {
        Resolver exact = keys.get(path);

        if (exact != null) {
            return exact.resolve(playerId);
        }

        if (groups.isEmpty()) {
            return null;
        }

        int separator = path.indexOf('.');

        if (separator <= 0 || separator == path.length() - 1) {
            return null;
        }

        GroupResolver group = groups.get(path.substring(0, separator));

        if (group == null) {
            return null;
        }

        return group.resolve(playerId, path.substring(separator + 1));
    }

    public boolean containsKey(String key) {
        return keys.containsKey(key);
    }

    public List<String> keys() {
        return published;
    }

    private static List<String> publish(Map<String, Resolver> keys, Map<String, GroupResolver> groups) {
        List<String> all = new ArrayList<>(keys.size() + groups.size());
        all.addAll(keys.keySet());

        for (String group : groups.keySet()) {
            all.add(group + GROUP_SUFFIX);
        }

        all.sort(String::compareTo);
        return List.copyOf(all);
    }

    static String requireValidKey(String key) {
        Objects.requireNonNull(key, "key");

        if (key.isEmpty()) {
            throw new IllegalArgumentException("placeholder key is empty");
        }

        int segments = 1;
        int segmentLength = 0;

        for (int index = 0; index < key.length(); index++) {
            char character = key.charAt(index);

            if (character == '.') {
                if (segmentLength == 0) {
                    throw new IllegalArgumentException("placeholder key has an empty segment: " + key);
                }

                segments++;
                segmentLength = 0;
                continue;
            }

            if (!isSegmentCharacter(character)) {
                throw new IllegalArgumentException("placeholder key must match [a-z0-9-] separated by '.': " + key);
            }

            segmentLength++;
        }

        if (segmentLength == 0) {
            throw new IllegalArgumentException("placeholder key has an empty segment: " + key);
        }

        if (segments > MAX_SEGMENTS) {
            throw new IllegalArgumentException("placeholder key exceeds " + MAX_SEGMENTS + " segments: " + key);
        }

        return key;
    }

    static String requireValidGroup(String group) {
        String validated = requireValidKey(group);

        if (validated.indexOf('.') >= 0) {
            throw new IllegalArgumentException("placeholder group must be a single segment: " + group);
        }

        return validated;
    }

    private static boolean isSegmentCharacter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '-';
    }

    @FunctionalInterface
    public interface Resolver {
        String resolve(UUID playerId);
    }

    @FunctionalInterface
    public interface GroupResolver {
        String resolve(UUID playerId, String tail);
    }

    public static final class Builder {
        private final Map<String, Resolver> keys = new HashMap<>();
        private final Map<String, GroupResolver> groups = new HashMap<>();

        private Builder() {
        }

        public Builder key(String key, Resolver resolver) {
            Objects.requireNonNull(resolver, "resolver");
            String validated = requireValidKey(key);

            if (keys.putIfAbsent(validated, resolver) != null) {
                throw new IllegalArgumentException("duplicate placeholder key: " + validated);
            }

            return this;
        }

        public Builder group(String group, GroupResolver resolver) {
            Objects.requireNonNull(resolver, "resolver");
            String validated = requireValidGroup(group);

            if (groups.putIfAbsent(validated, resolver) != null) {
                throw new IllegalArgumentException("duplicate placeholder group: " + validated);
            }

            return this;
        }

        public PlaceholderKeyRegistry build() {
            return new PlaceholderKeyRegistry(keys, groups);
        }
    }
}
