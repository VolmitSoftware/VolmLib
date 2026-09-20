package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class NativeBiomeRegistry {
    private final Supplier<NativeModdedServer> server;
    private final String fallbackKey;
    private volatile View cache;

    public NativeBiomeRegistry(Supplier<NativeModdedServer> server, String fallbackKey) {
        this.server = server;
        this.fallbackKey = fallbackKey;
    }

    public View current() {
        NativeModdedServer host = server.get();
        MinecraftServer instance = host == null ? null : host.server();
        if (instance == null) {
            return null;
        }
        Registry<Biome> registry = instance.registryAccess().lookupOrThrow(Registries.BIOME);
        View current = cache;
        if (current != null && current.registry == registry) {
            return current;
        }
        View replacement = new View(registry, fallbackKey);
        cache = replacement;
        return replacement;
    }

    public static final class View {
        private static final int MAX_CACHED_IDS = 4096;
        private static final char SCOPE_SEPARATOR = (char) 0;
        private final Registry<Biome> registry;
        private final String fallbackKey;
        private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
        private volatile List<NativeBiome> biomes;

        private View(Registry<Biome> registry, String fallbackKey) {
            this.registry = registry;
            this.fallbackKey = fallbackKey;
        }

        public int resolve(String key, String scoped, Function<String, String> derivative) {
            String cacheKey = scoped.equals(key) ? key : key + SCOPE_SEPARATOR + scoped;
            Integer hit = ids.get(cacheKey);
            if (hit != null) {
                return hit;
            }
            int resolved = idForKey(scoped);
            if (resolved < 0) {
                String derivativeKey = derivative.apply(key);
                resolved = derivativeKey == null ? -1 : idForKey(derivativeKey);
            }
            if (resolved < 0) {
                resolved = fallbackId();
            }
            if (ids.size() < MAX_CACHED_IDS) {
                ids.put(cacheKey, resolved);
            }
            return resolved;
        }

        public int fallbackId() {
            int fallback = idForKey(fallbackKey);
            return fallback >= 0 ? fallback : 0;
        }

        public List<NativeBiome> allBiomes() {
            List<NativeBiome> snapshot = biomes;
            if (snapshot == null) {
                List<NativeBiome> built = new ArrayList<>();
                for (Identifier identifier : registry.keySet()) {
                    Biome biome = registry.getValue(identifier);
                    if (biome != null) {
                        built.add(ModdedBiome.of(biome, identifier.toString()));
                    }
                }
                snapshot = List.copyOf(built);
                biomes = snapshot;
            }
            return new ArrayList<>(snapshot);
        }

        private int idForKey(String key) {
            Identifier identifier = Identifier.tryParse(key);
            if (identifier == null) {
                return -1;
            }
            Biome biome = registry.getValue(identifier);
            return biome == null ? -1 : registry.getId(biome);
        }
    }
}
