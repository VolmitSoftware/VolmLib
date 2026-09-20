package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.BiomeLocation;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.HorizontalQuery;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.ClosestQuery;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.NearbyQuery;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.Column;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy.ColumnResolver;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Set;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class NativeBiomeSourceImpl extends BiomeSource implements StructureBiomeSource {
    private final NativeBiomeSourcePolicy<Holder<Biome>> policy;

    public NativeBiomeSourceImpl(NativeBiomeSourcePolicy<Holder<Biome>> policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return policy.possibleBiomes().stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        return policy.possibleBiomes();
    }

    @Override
    public Set<Holder<Biome>> possibleStructureBiomes() {
        return policy.possibleStructureBiomes();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return policy.getNoiseBiome(x, y, z);
    }

    public Holder<Biome> getVisibleNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return policy.getVisibleNoiseBiome(x, y, z);
    }

    public Holder<Biome> getVisibleSurfaceBiome(int blockX, int blockZ) {
        return policy.getVisibleSurfaceBiome(blockX, blockZ);
    }

    public Holder<Biome> getVanillaSpawnBiome(Holder<Biome> biome) {
        return policy.getVanillaSpawnBiome(biome);
    }

    public Holder<Biome> getRetainedVanillaSpawnBiome(String key) {
        return policy.getRetainedVanillaSpawnBiome(key);
    }

    public void prepareVisibleBiomeBatch() {
        policy.prepareVisibleBiomeBatch();
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findBiomeHorizontal(int x, int y, int z, int radius,
            Predicate<Holder<Biome>> allowed, RandomSource random, Climate.Sampler sampler) {
        return position(policy.findBiomeHorizontal(new HorizontalQuery<>(x, y, z, radius,
                allowed, random::nextInt,
                () -> location(super.findBiomeHorizontal(x, y, z, radius, allowed, random, sampler)))));
    }

    @Override
    public Pair<BlockPos, Holder<Biome>> findClosestBiome3d(BlockPos origin, int radius,
            int horizontalResolution, int verticalResolution, Predicate<Holder<Biome>> allowed,
            Climate.Sampler sampler, LevelReader level) {
        SearchGeometry geometry = new SearchGeometry(origin, radius, horizontalResolution, verticalResolution,
                level.getMinY(), level.getMaxY());
        return position(policy.findClosestBiome3d(new ClosestQuery<>(allowed,
                () -> location(super.findClosestBiome3d(origin, radius, horizontalResolution,
                        verticalResolution, allowed, sampler, level)),
                (candidates, resolver) -> search(geometry, candidates, resolver))));
    }

    @Override
    public Set<Holder<Biome>> getBiomesWithin(int x, int y, int z, int radius, Climate.Sampler sampler) {
        return policy.getBiomesWithin(new NearbyQuery<>(x, y, z, radius,
                () -> super.getBiomesWithin(x, y, z, radius, sampler)));
    }

    private static BiomeLocation<Holder<Biome>> search(SearchGeometry geometry, Set<Holder<Biome>> candidates,
                                                       ColumnResolver<Holder<Biome>> resolver) {
        int sampleRadius = Math.floorDiv(geometry.radius(), geometry.horizontalResolution());
        int[] sampleYs = Mth.outFromOrigin(geometry.origin().getY(), geometry.minimumY() + 1,
                geometry.maximumY() + 1, geometry.verticalResolution()).toArray();
        for (BlockPos.MutableBlockPos sample : BlockPos.spiralAround(
                BlockPos.ZERO, sampleRadius, Direction.EAST, Direction.SOUTH)) {
            int x = geometry.origin().getX() + sample.getX() * geometry.horizontalResolution();
            int z = geometry.origin().getZ() + sample.getZ() * geometry.horizontalResolution();
            Column<Holder<Biome>> column = resolver.resolve(x, z);
            for (int y : sampleYs) {
                Holder<Biome> biome = column.sample(y);
                if (candidates.contains(biome)) {
                    return new BiomeLocation<>(x, y, z, biome);
                }
            }
        }
        return null;
    }

    private static BiomeLocation<Holder<Biome>> location(Pair<BlockPos, Holder<Biome>> pair) {
        return pair == null ? null : new BiomeLocation<>(pair.getFirst().getX(), pair.getFirst().getY(),
                pair.getFirst().getZ(), pair.getSecond());
    }

    private static Pair<BlockPos, Holder<Biome>> position(BiomeLocation<Holder<Biome>> location) {
        return location == null ? null : Pair.of(new BlockPos(location.x(), location.y(), location.z()), location.biome());
    }

    private record SearchGeometry(BlockPos origin, int radius, int horizontalResolution, int verticalResolution,
                                  int minimumY, int maximumY) {
    }
}
