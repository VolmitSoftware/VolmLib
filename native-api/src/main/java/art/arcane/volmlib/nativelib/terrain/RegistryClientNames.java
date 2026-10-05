package art.arcane.volmlib.nativelib.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class RegistryClientNames {
    private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static volatile Map<String, Map<String, String>> registrations = Map.of();

    private RegistryClientNames() {
    }

    public static synchronized void register(String registryKey, String physicalResourceKey, String readableResourceKey) {
        String registry = requireResourceKey(registryKey);
        String physical = requireResourceKey(physicalResourceKey);
        String readable = requireResourceKey(readableResourceKey);
        Map<String, String> current = registrations.getOrDefault(registry, Map.of());
        String existing = current.get(physical);
        if (existing != null) {
            if (!existing.equals(readable)) {
                throw new IllegalArgumentException("Registry resource " + registry + "/" + physical
                        + " already has a different client name: " + existing);
            }
            return;
        }
        Map<String, String> resources = new HashMap<>(current);
        resources.put(physical, readable);
        Map<String, Map<String, String>> updated = new HashMap<>(registrations);
        updated.put(registry, Map.copyOf(resources));
        registrations = Map.copyOf(updated);
    }

    public static List<String> resolve(String registryKey, List<String> originalKeys) {
        String registry = requireResourceKey(registryKey);
        List<String> originals = List.copyOf(Objects.requireNonNull(originalKeys, "originalKeys"));
        Map<String, String> names = registrations.getOrDefault(registry, Map.of());
        if (names.isEmpty()) {
            return originals;
        }
        Set<String> occupied = new HashSet<>();
        Set<String> reservedBases = new HashSet<>();
        Map<String, Set<String>> groups = new TreeMap<>();
        for (String physical : originals) {
            String readable = names.get(physical);
            if (readable == null) {
                occupied.add(physical);
                continue;
            }
            reservedBases.add(readable);
            groups.computeIfAbsent(readable, ignored -> new TreeSet<>()).add(physical);
        }
        Map<String, String> resolved = new HashMap<>();
        for (Map.Entry<String, Set<String>> group : groups.entrySet()) {
            String base = group.getKey();
            for (String physical : group.getValue()) {
                String readable = availableName(base, occupied, reservedBases);
                occupied.add(readable);
                resolved.put(physical, readable);
            }
        }
        List<String> result = new ArrayList<>(originals.size());
        for (String physical : originals) {
            result.add(resolved.getOrDefault(physical, physical));
        }
        return List.copyOf(result);
    }

    private static String availableName(String base, Set<String> occupied, Set<String> reservedBases) {
        if (!occupied.contains(base)) {
            return base;
        }
        int separator = base.indexOf(':');
        String namespace = base.substring(0, separator);
        String path = base.substring(separator);
        for (int ordinal = 2; ordinal < Integer.MAX_VALUE; ordinal++) {
            String candidate = namespace + "_" + ordinal + path;
            if (!occupied.contains(candidate) && !reservedBases.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No available client registry name for " + base);
    }

    private static String requireResourceKey(String resourceKey) {
        String required = Objects.requireNonNull(resourceKey, "resourceKey");
        if (!RESOURCE_KEY.matcher(required).matches()) {
            throw new IllegalArgumentException("Invalid registry resource key: " + required);
        }
        return required;
    }
}
