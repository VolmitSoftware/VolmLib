package art.arcane.volmlib.nativelib;

import org.bukkit.Bukkit;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

public final class NativeAdapters {
    private static volatile ClassLoader providerLoader;
    private NativeAdapters() {
    }

    public static synchronized void registerProviderLoader(ClassLoader loader) {
        providerLoader = Objects.requireNonNull(loader, "loader");
    }

    public static synchronized void releaseProviderLoader(ClassLoader loader) {
        if (providerLoader == loader) {
            providerLoader = null;
        }
    }

    public static <T> Optional<T> find(Class<T> capability) {
        Objects.requireNonNull(capability, "capability");
        if (Bukkit.getServer() == null) {
            return Optional.empty();
        }
        return find(capability, Bukkit.getBukkitVersion());
    }

    public static <T> Optional<T> find(Class<T> capability, String minecraftVersion) {
        Objects.requireNonNull(capability, "capability");
        NativeBinding binding = binding(capability);
        Optional<NativeVersion> version = NativeVersion.resolve(minecraftVersion);
        if (version.isEmpty()) {
            return Optional.empty();
        }
        String implementation = NativeAdapters.class.getPackageName() + "."
                + version.get().packageName() + "." + binding.value();
        try {
            Class<?> type = Class.forName(implementation, true, implementationLoader(capability));
            return Optional.of(capability.cast(type.getConstructor().newInstance()));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Native capability initialization failed: " + implementation,
                    exception.getCause());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError exception) {
            throw new IllegalStateException("Native capability cannot be loaded: " + implementation, exception);
        }
    }

    public static <T> T require(Class<T> capability) {
        return require(capability, Bukkit.getBukkitVersion());
    }

    public static <T> T require(Class<T> capability, String minecraftVersion) {
        return find(capability, minecraftVersion).orElseThrow(() -> new UnsupportedOperationException(
                "Native capability " + capability.getName() + " is unavailable for " + minecraftVersion));
    }

    public static boolean available(Class<?> capability, NativeVersion version) {
        Objects.requireNonNull(version, "version");
        NativeBinding binding = binding(Objects.requireNonNull(capability, "capability"));
        String implementation = NativeAdapters.class.getPackageName() + "."
                + version.packageName() + "." + binding.value();
        try {
            return capability.isAssignableFrom(Class.forName(implementation, false, implementationLoader(capability)));
        } catch (ClassNotFoundException absent) {
            return false;
        } catch (LinkageError exception) {
            throw new IllegalStateException("Native capability cannot be loaded: " + implementation, exception);
        }
    }

    private static ClassLoader implementationLoader(Class<?> capability) {
        ClassLoader loader = providerLoader;
        return loader == null ? capability.getClassLoader() : loader;
    }

    private static NativeBinding binding(Class<?> capability) {
        NativeBinding binding = capability.getAnnotation(NativeBinding.class);
        if (binding == null) {
            throw new IllegalArgumentException("Native capability has no binding: " + capability.getName());
        }
        return binding;
    }
}
