package art.arcane.volmlib.util.bukkit.registry;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public final class RegistryUtil {
    private static final AtomicReference<RegistryLookup> REGISTRY_LOOKUP = new AtomicReference<>();
    private static final Map<Class<?>, Map<NamespacedKey, Keyed>> KEYED_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Map<NamespacedKey, Object>> ENUM_REGISTRY = new HashMap<>();
    private static final Map<Class<?>, Registry<Keyed>> REGISTRY = new HashMap<>();

    private RegistryUtil() {
    }

    public static <T> T find(Class<T> typeClass, String... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    public static <T> T find(Class<T> typeClass, Lookup<T> lookup, String... keys) {
        NamespacedKey[] namespacedKeys = Arrays.stream(keys)
                .map(NamespacedKey::minecraft)
                .toArray(NamespacedKey[]::new);
        return find(typeClass, lookup, namespacedKeys);
    }

    public static <T> T find(Class<T> typeClass, NamespacedKey... keys) {
        return find(typeClass, defaultLookup(), keys);
    }

    public static <T> T find(Class<T> typeClass, Lookup<T> lookup, NamespacedKey... keys) {
        if (keys.length == 0) {
            throw new IllegalArgumentException("Need at least one key");
        }

        Registry<Keyed> registry = null;
        if (Keyed.class.isAssignableFrom(typeClass)) {
            registry = getRegistry(typeClass.asSubclass(Keyed.class));
        }

        if (registry == null) {
            registry = REGISTRY.computeIfAbsent(typeClass, t -> Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .filter(field -> ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(t))
                    .map(field -> {
                        try {
                            return (Registry<Keyed>) field.get(null);
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null));
        }

        if (registry != null) {
            for (NamespacedKey key : keys) {
                Keyed value = registry.get(key);
                if (value != null) {
                    return (T) value;
                }
            }
        }

        if (lookup != null) {
            return lookup.find(typeClass, keys);
        }

        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    public static <T> T findByField(Class<T> typeClass, NamespacedKey... keys) {
        Map<NamespacedKey, Keyed> values = KEYED_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getKeyedValues);
        for (NamespacedKey key : keys) {
            Keyed value = values.get(key);
            if (value != null) {
                return (T) value;
            }
        }

        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    public static <T> T findByEnum(Class<T> typeClass, NamespacedKey... keys) {
        Map<NamespacedKey, Object> values = ENUM_REGISTRY.computeIfAbsent(typeClass, RegistryUtil::getEnumValues);
        for (NamespacedKey key : keys) {
            Object value = values.get(key);
            if (value != null) {
                return (T) value;
            }
        }

        throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
    }

    public static <T> Lookup<T> defaultLookup() {
        return Lookup.combine(RegistryUtil::findByField, RegistryUtil::findByEnum);
    }

    private static Map<NamespacedKey, Keyed> getKeyedValues(Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> Keyed.class.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return (Keyed) field.get(null);
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Keyed::getKey, Function.identity()));
    }

    private static Map<NamespacedKey, Object> getEnumValues(Class<?> typeClass) {
        return Arrays.stream(typeClass.getDeclaredFields())
                .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                .filter(field -> typeClass.isAssignableFrom(field.getType()))
                .map(field -> {
                    try {
                        return Map.entry(NamespacedKey.minecraft(field.getName().toLowerCase()), field.get(null));
                    } catch (Throwable e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @FunctionalInterface
    public interface Lookup<T> {
        T find(Class<T> typeClass, NamespacedKey... keys);

        @SafeVarargs
        static <T> Lookup<T> combine(Lookup<T>... lookups) {
            if (lookups.length == 0) {
                throw new IllegalArgumentException("Need at least one lookup");
            }

            return (typeClass, keys) -> {
                for (Lookup<T> lookup : lookups) {
                    try {
                        return lookup.find(typeClass, keys);
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                throw new IllegalArgumentException("No element found for keys: " + Arrays.toString(keys));
            };
        }
    }

    private static <T extends Keyed> Registry<T> getRegistry(Class<T> typeClass) {
        RegistryLookup lookup = REGISTRY_LOOKUP.updateAndGet(old -> {
            if (old != null) {
                return old;
            }

            RegistryLookup bukkit;
            try {
                bukkit = Bukkit::getRegistry;
            } catch (Throwable ignored) {
                bukkit = null;
            }

            return new DefaultRegistryLookup(bukkit);
        });

        return lookup.find(typeClass);
    }

    private interface RegistryLookup {
        <T extends Keyed> Registry<T> find(Class<T> type);
    }

    private static final class DefaultRegistryLookup implements RegistryLookup {
        private final RegistryLookup bukkit;
        private final Map<Type, Object> registries;

        private DefaultRegistryLookup(RegistryLookup bukkit) {
            this.bukkit = bukkit;
            this.registries = Arrays.stream(Registry.class.getDeclaredFields())
                    .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
                    .filter(field -> Registry.class.isAssignableFrom(field.getType()))
                    .map(field -> {
                        Type type = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                        try {
                            return Map.entry(type, field.get(null));
                        } catch (Throwable e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        }

        @Override
        public <T extends Keyed> Registry<T> find(Class<T> type) {
            if (bukkit == null) {
                return (Registry<T>) registries.get(type);
            }

            try {
                return bukkit.find(type);
            } catch (Throwable e) {
                return (Registry<T>) registries.get(type);
            }
        }
    }
}
