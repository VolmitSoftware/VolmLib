package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class NativeModdedBiomeSource<P extends NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>>
        extends BiomeSource implements NativeFeatureBiomeSource {
    private final BiomeSource serializedSource;
    private final P policy;
    private final Access access;

    public NativeModdedBiomeSource(Options<P> options) {
        serializedSource = options.serializedSource();
        access = new Access(options.server());
        policy = options.policyFactory().apply(access);
    }

    public P policy() {
        return policy;
    }

    public void clearCaches() {
        access.current = null;
        policy.clearCaches();
    }

    public void evictRuntime(int runtimeIdentity) {
        policy.evictRuntime(runtimeIdentity);
    }

    public long packGeneration() {
        return policy.packGeneration();
    }

    public BiomeSource forStructureState(HolderLookup<StructureSet> structureSets) {
        LinkedHashSet<Holder<Biome>> possible = new LinkedHashSet<>();
        RegistryEntries registry = access.registry();
        if (registry == null) {
            throw new IllegalStateException("Cannot create structure state without the biome registry");
        }
        Set<String> generatedKeys = policy.structureBiomeKeys();
        Set<String> missing = new LinkedHashSet<>(generatedKeys);
        registry.forEach(biome -> missing.remove(key(biome)));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Structure biomes are not registered: " + missing);
        }
        structureSets.listElements().forEach(reference -> {
            for (StructureSet.StructureSelectionEntry entry : reference.value().structures()) {
                for (Holder<Biome> biome : entry.structure().value().biomes()) {
                    if (generatedKeys.contains(key(biome))) {
                        possible.add(biome);
                    }
                }
            }
        });
        return new StructureStateBiomeSource(this, Set.copyOf(possible));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Runtime biome sources are serialized through their chunk generator");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return policy.orderedPossibleBiomes().stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return policy.possibleBiomes();
    }

    @Override
    public List<Holder<Biome>> orderedPossibleBiomes() {
        return policy.orderedPossibleBiomes();
    }

    @Override
    public Holder<Biome> registeredBiome(String key) {
        return policy.registeredBiome(key);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return policy.getNoiseBiome(x, y, z, sampler);
    }

    public Holder<Biome> getVisibleNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return policy.getVisibleNoiseBiome(x, y, z, sampler);
    }

    public Holder<Biome> getVisibleSurfaceBiome(int x, int z) {
        return policy.getVisibleSurfaceBiome(x, z);
    }

    public boolean isStructureReachable(Holder<Structure> structure) {
        return policy.isStructureReachable(structure.value().biomes());
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int searchRadius,
            int sampleResolutionHorizontal, int sampleResolutionVertical, Predicate<Holder<Biome>> allowed,
            Climate.Sampler sampler, LevelReader level) {
        NativeBiomeSourcePolicy.BiomeLocation<Holder<Biome>> found = policy.findClosestBiome3d(
                new NativeModdedBiomePolicy.ClosestQuery<>(allowed, sampler,
                        () -> location(serializedSource.findClosestBiome3d(origin, searchRadius,
                                sampleResolutionHorizontal, sampleResolutionVertical, allowed, sampler, level)),
                        () -> location(super.findClosestBiome3d(origin, searchRadius,
                                sampleResolutionHorizontal, sampleResolutionVertical, allowed, sampler, level)),
                        (candidates, resolver) -> search(new Search(origin, searchRadius,
                                sampleResolutionHorizontal, sampleResolutionVertical, level), candidates, resolver)));
        return found == null ? null : Pair.of(new BlockPos(found.x(), found.y(), found.z()), found.biome());
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        return policy.getBiomesWithin(new NativeModdedBiomePolicy.NearbyQuery<>(x, y, z, radius, sampler,
                () -> super.getBiomesWithin(x, y, z, radius, sampler)));
    }

    private static String key(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString().toLowerCase(Locale.ROOT)).orElse(null);
    }

    private static NativeBiomeSourcePolicy.BiomeLocation<Holder<Biome>> location(Pair<BlockPos, Holder<Biome>> pair) {
        if (pair == null) {
            return null;
        }
        BlockPos position = pair.getFirst();
        return new NativeBiomeSourcePolicy.BiomeLocation<>(position.getX(), position.getY(), position.getZ(), pair.getSecond());
    }

    private static NativeBiomeSourcePolicy.BiomeLocation<Holder<Biome>> search(Search search,
            Set<Holder<Biome>> candidates, NativeBiomeSourcePolicy.ColumnResolver<Holder<Biome>> resolver) {
        int sampleRadius = Math.floorDiv(search.radius(), search.horizontalResolution());
        int[] sampleYs = Mth.outFromOrigin(search.origin().getY(), search.level().getMinY() + 1,
                search.level().getMaxY() + 1, search.verticalResolution()).toArray();
        for (BlockPos.MutableBlockPos column : BlockPos.spiralAround(BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
            int x = search.origin().getX() + column.getX() * search.horizontalResolution();
            int z = search.origin().getZ() + column.getZ() * search.horizontalResolution();
            NativeBiomeSourcePolicy.Column<Holder<Biome>> sample = resolver.resolve(x, z);
            for (int y : sampleYs) {
                Holder<Biome> biome = sample.sample(y);
                if (candidates.contains(biome)) {
                    return new NativeBiomeSourcePolicy.BiomeLocation<>(x, y, z, biome);
                }
            }
        }
        return null;
    }

    public record Options<P extends NativeModdedBiomePolicy<Holder<Biome>, Climate.Sampler>>(
            BiomeSource serializedSource, Supplier<MinecraftServer> server,
            Function<NativeBiomeSourceAccess<Holder<Biome>, Climate.Sampler>, P> policyFactory) {
    }

    private record Search(BlockPos origin, int radius, int horizontalResolution, int verticalResolution, LevelReader level) {
    }

    private final class Access implements NativeBiomeSourceAccess<Holder<Biome>, Climate.Sampler> {
        private final Supplier<MinecraftServer> server;
        private volatile RegistryEntries current;

        private Access(Supplier<MinecraftServer> server) {
            this.server = server;
        }

        @Override
        public RegistryEntries registry() {
            MinecraftServer active = server.get();
            if (active == null) {
                current = null;
                return null;
            }
            Registry<Biome> registry = active.registryAccess().lookupOrThrow(Registries.BIOME);
            RegistryEntries cached = current;
            if (cached == null || cached.registry() != registry) {
                cached = new RegistryEntries(registry);
                current = cached;
            }
            return cached;
        }

        @Override
        public Set<Holder<Biome>> serializedBiomes() {
            return serializedSource.possibleBiomes();
        }

        @Override
        public Holder<Biome> serializedNoise(int x, int y, int z, Climate.Sampler sampler) {
            return serializedSource.getNoiseBiome(x, y, z, sampler);
        }

        @Override
        public String holderKey(Holder<Biome> holder) {
            return key(holder);
        }
    }

    private record RegistryEntries(Registry<Biome> registry) implements NativeBiomeSourceAccess.RegistryView<Holder<Biome>> {
        @Override
        public void forEach(Consumer<? super Holder<Biome>> consumer) {
            registry.listElements().forEach(consumer);
        }

        @Override
        public Holder<Biome> lookup(String key) {
            Identifier identifier = key == null || key.isBlank() ? null : Identifier.tryParse(key);
            return identifier == null ? null : registry.get(identifier).orElse(null);
        }
    }

    private static final class StructureStateBiomeSource extends BiomeSource {
        private final NativeModdedBiomeSource<?> delegate;
        private final Set<Holder<Biome>> possible;

        private StructureStateBiomeSource(NativeModdedBiomeSource<?> delegate, Set<Holder<Biome>> possible) {
            this.delegate = delegate;
            this.possible = possible;
        }

        @Override
        protected MapCodec<? extends BiomeSource> codec() {
            throw new UnsupportedOperationException("Structure state biome sources are not serializable");
        }

        @Override
        protected Stream<Holder<Biome>> collectPossibleBiomes() {
            return possible.stream();
        }

        @Override
        public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
            return delegate.policy.requiredStructureBiome(x, y, z, sampler);
        }

        @Override
        public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int searchRadius,
                Predicate<Holder<Biome>> allowed, RandomSource random, Climate.Sampler sampler) {
            int step = delegate.policy.horizontalSearchStep(y, searchRadius);
            return step == 1 ? super.findBiomeHorizontal(x, y, z, searchRadius, allowed, random, sampler)
                    : super.findBiomeHorizontal(x, y, z, searchRadius, step, allowed, random, false, sampler);
        }
    }
}
