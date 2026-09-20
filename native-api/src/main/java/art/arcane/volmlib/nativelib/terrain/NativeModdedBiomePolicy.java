package art.arcane.volmlib.nativelib.terrain;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface NativeModdedBiomePolicy<H, S> {
    void clearCaches();
    void evictRuntime(int runtimeIdentity);
    long packGeneration();
    Set<H> possibleBiomes();
    List<H> orderedPossibleBiomes();
    H registeredBiome(String key);
    H getNoiseBiome(int x, int y, int z, S sampler);
    H getVisibleNoiseBiome(int x, int y, int z, S sampler);
    H getVisibleSurfaceBiome(int blockX, int blockZ);
    H requiredStructureBiome(int x, int y, int z, S sampler);
    Set<String> structureBiomeKeys();
    boolean isStructureReachable(Iterable<H> biomes);
    int horizontalSearchStep(int blockY, int searchRadius);
    NativeBiomeSourcePolicy.BiomeLocation<H> findClosestBiome3d(ClosestQuery<H, S> query);
    Set<H> getBiomesWithin(NearbyQuery<H, S> query);

    record ClosestQuery<H, S>(Predicate<H> allowed, S sampler,
                             Supplier<NativeBiomeSourcePolicy.BiomeLocation<H>> unboundFallback,
                             Supplier<NativeBiomeSourcePolicy.BiomeLocation<H>> defaultFallback,
                             NativeBiomeSourcePolicy.ClosestSearch<H> search) {
    }

    record NearbyQuery<H, S>(int x, int y, int z, int radius, S sampler, Supplier<Set<H>> fallback) {
    }
}
