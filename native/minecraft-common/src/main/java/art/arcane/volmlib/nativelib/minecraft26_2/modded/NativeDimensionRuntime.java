package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class NativeDimensionRuntime {
    private final ModdedServerAccess access;

    public NativeDimensionRuntime(ModdedServerAccess access) {
        this.access = Objects.requireNonNull(access);
    }

    public boolean hasDimensionType(NativeModdedServer host, String key) {
        return host.server().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(key))).isPresent();
    }

    public <G extends NativeGeneratorOwner> Created<G> create(NativeModdedServer host, Creation<G> options) {
        MinecraftServer server = host.server();
        RegistryAccess registry = server.registryAccess();
        Holder<DimensionType> dimensionType = registry.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(options.dimensionType())));
        FixedBiomeSource biomes = new FixedBiomeSource(registry.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        G generator = options.generator().apply(new NativeGeneratorContext(biomes, options.generatorKey()));
        LevelStem stem = new LevelStem(dimensionType, (ChunkGenerator) generator.nativeGenerator());
        WorldData data = server.getWorldData();
        ServerLevel level = new ServerLevel(server, access.levelExecutor(server), access.levelStorage(server),
                new DerivedLevelData(data, data.overworldData()), key(options.dimension()), stem, false,
                BiomeManager.obfuscateSeed(options.seed()), List.of(), false);
        return new Created<>(new ModdedPlatformWorld(level), generator);
    }

    public boolean hasLevel(NativeModdedServer host, String dimension) {
        return access.hasLevel(host.server(), key(dimension));
    }

    public void initialize(NativeModdedServer host, NativeWorld world) {
        access.initializeLevelData(host.server(), level(world));
    }

    public NativeWorld publish(NativeModdedServer host, NativeWorld world) {
        ServerLevel previous = access.putLevelIfAbsent(host.server(), key(world.name()), level(world));
        return previous == null ? null : new ModdedPlatformWorld(previous);
    }

    public NativeWorld remove(NativeModdedServer host, String dimension) {
        ServerLevel removed = access.removeLevel(host.server(), key(dimension));
        return removed == null ? null : new ModdedPlatformWorld(removed);
    }

    public void restore(NativeModdedServer host, NativeWorld world) {
        access.putLevel(host.server(), key(world.name()), level(world));
    }

    public void addWorldBorderListener(NativeModdedServer host, NativeWorld world) {
        host.server().getPlayerList().addWorldborderListener(level(world));
    }

    public void save(NativeWorld world) {
        level(world).save(null, true, false);
    }

    public void close(NativeWorld world) throws IOException {
        level(world).close();
    }

    public static boolean sameWorld(NativeWorld first, NativeWorld second) {
        return first != null && second != null && first.nativeHandle() == second.nativeHandle();
    }

    private static ServerLevel level(NativeWorld world) {
        return (ServerLevel) world.nativeHandle();
    }

    private static ResourceKey<Level> key(String dimension) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
    }

    public record Creation<G extends NativeGeneratorOwner>(String dimension, String dimensionType, long seed, String generatorKey,
                              Function<NativeGeneratorContext, G> generator) {
    }

    public record Created<G>(NativeWorld world, G generator) {
    }
}
