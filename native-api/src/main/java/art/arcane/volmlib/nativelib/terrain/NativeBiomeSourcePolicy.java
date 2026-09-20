package art.arcane.volmlib.nativelib.terrain;

import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface NativeBiomeSourcePolicy<H> {
    Set<H> possibleBiomes();

    Set<H> possibleStructureBiomes();

    H getNoiseBiome(int x, int y, int z);

    H getVisibleNoiseBiome(int x, int y, int z);

    H getVisibleSurfaceBiome(int blockX, int blockZ);

    H getVanillaSpawnBiome(H biome);

    H getRetainedVanillaSpawnBiome(String derivativeKey);

    void prepareVisibleBiomeBatch();

    VisibleResolver<H> visibleResolver();

    BiomeLocation<H> findBiomeHorizontal(HorizontalQuery<H> query);

    BiomeLocation<H> findClosestBiome3d(ClosestQuery<H> query);

    Set<H> getBiomesWithin(NearbyQuery<H> query);

    record BiomeLocation<H>(int x, int y, int z, H biome) {
    }

    record HorizontalQuery<H>(int x, int y, int z, int radius, Predicate<H> allowed,
                              IntUnaryOperator random, Supplier<BiomeLocation<H>> fallback) {
    }

    record ClosestQuery<H>(Predicate<H> allowed, Supplier<BiomeLocation<H>> fallback, ClosestSearch<H> search) {
    }

    record NearbyQuery<H>(int x, int y, int z, int radius, Supplier<Set<H>> fallback) {
    }

    @FunctionalInterface
    interface ClosestSearch<H> {
        BiomeLocation<H> search(Set<H> candidates, ColumnResolver<H> resolver);
    }

    @FunctionalInterface
    interface ColumnResolver<H> {
        Column<H> resolve(int blockX, int blockZ);
    }

    @FunctionalInterface
    interface Column<H> {
        H sample(int blockY);
    }
    @FunctionalInterface
    interface VisibleResolver<H> {
        H biome(int quartX, int quartY, int quartZ);
    }

}
