package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.cache.FloatCache;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CNGDescendantMutationTest {
    @Test
    public void descendantMutatorsAndSettersRetireSignedTwoDimensionalCoordinates() {
        descendantMutations(false);
    }

    @Test
    public void descendantMutatorsAndSettersRetireSignedThreeDimensionalCoordinates() {
        descendantMutations(true);
    }

    @Test
    public void sharedDescendantRetiresAffectedFloatCachesAndPreservesUnrelatedSnapshot() {
        CNG shared = simplex(17L);
        CNG first = simplex(31L).fractureWith(shared, 32D);
        CNG second = simplex(41L).child(shared);
        CNG unrelated = simplex(53L);
        FloatCache unrelatedSnapshot = installSnapshot(unrelated);
        installSnapshot(first);
        installSnapshot(second);
        first.noiseFastSigned2D(1D, 2D);
        second.noiseFastSigned2D(1D, 2D);
        shared.scale(0.5D);
        assertNull(first.getCache());
        assertNull(second.getCache());
        assertSame(unrelatedSnapshot, unrelated.getCache());
        assertEquals(Double.doubleToRawLongBits(sample(simplex(31L).fractureWith(simplex(17L).scale(0.5D), 32D), false)),
                Double.doubleToRawLongBits(sample(first, false)));
    }

    @Test
    public void replacingEdgesKeepsRemainingAliasesAndRetiresDetachedParents() {
        CNG old = simplex(17L);
        CNG replacement = simplex(19L);
        CNG parent = simplex(31L).fractureWith(old, 32D).child(old);
        parent.setFracture(replacement);
        installSnapshot(parent);
        old.scale(0.5D);
        assertNull(parent.getCache());
        parent.setChildren(null);
        FloatCache snapshot = installSnapshot(parent);
        old.scale(0.25D);
        assertSame(snapshot, parent.getCache());
        replacement.scale(0.5D);
        assertNull(parent.getCache());
    }

    @Test(timeout = 1000L)
    public void cyclicParentLinksTerminateDuringConfigurationChanges() {
        CNG first = simplex(17L);
        CNG second = simplex(31L);
        first.fractureWith(second, 1D);
        second.fractureWith(first, 1D);
        first.scale(0.5D);
        second.setFracture(null);
        first.pow(1.25D);
    }

    @Test
    public void generatorSetterRefreshesScalingAndForwardsCurrentOctaves() {
        CNG noise = simplex(17L).scale(0.25D).oct(3);
        noise.noiseFastSigned2D(4D, 8D);
        RecordingNoise replacement = new RecordingNoise(true);
        noise.setGenerator(replacement);
        assertEquals(3, replacement.octaves);
        noise.noiseFastSigned2D(4D, 8D);
        assertEquals(4D, replacement.sampledX, 0D);
        assertEquals(8D, replacement.sampledZ, 0D);
    }

    @Test
    public void injectorSetterChangesTheActualMixingOperation() {
        CNG sampled = simplex(17L).child(simplex(19L));
        sample(sampled, false);
        sampled.setInjector(CNG.MULTIPLY);
        CNG expected = simplex(17L).child(simplex(19L)).injectWith(CNG.MULTIPLY);
        assertEquals(Double.doubleToRawLongBits(sample(expected, false)), Double.doubleToRawLongBits(sample(sampled, false)));
    }

    @Test
    public void cacheReuseRemainsLocalAfterAnUnrelatedGraphChanges() {
        RecordingNoise source = new RecordingNoise(false);
        CNG sampled = new CNG(new RNG(17L), source, 1D, 1).fractureWith(simplex(19L), 32D);
        sample(sampled, false);
        sample(sampled, false);
        assertEquals(1, source.calls);
        simplex(23L).scale(0.5D);
        sample(sampled, false);
        assertEquals(1, source.calls);
        sampled.getFracture().scale(0.5D);
        sample(sampled, false);
        sample(sampled, false);
        assertEquals(2, source.calls);
    }

    private static void descendantMutations(boolean threeDimensions) {
        List<Consumer<CNG>> mutations = List.of(
                noise -> noise.scale(0.5D), noise -> noise.up(0.125D), noise -> noise.pow(1.25D),
                noise -> noise.setScale(0.5D), noise -> noise.setBakedScale(0.5D),
                noise -> noise.setFscale(2D), noise -> noise.setPatch(0.75D),
                noise -> noise.setUp(0.125D), noise -> noise.setDown(0.125D),
                noise -> noise.setPower(1.25D), noise -> noise.setOct(3),
                noise -> noise.setFracture(simplex(29L)),
                noise -> noise.setChildren(new KList<CNG>().qadd(simplex(37L))));
        for (Consumer<CNG> mutation : mutations) {
            CNG sampled = graph();
            CNG expected = graph();
            sample(sampled, threeDimensions);
            mutation.accept(sampled.getFracture().getFracture());
            mutation.accept(expected.getFracture().getFracture());
            assertEquals(Double.doubleToRawLongBits(sample(expected, threeDimensions)),
                    Double.doubleToRawLongBits(sample(sampled, threeDimensions)));
        }
    }

    private static CNG graph() {
        return simplex(11L).fractureWith(simplex(13L).fractureWith(
                simplex(17L).fractureWith(simplex(19L), 8D), 16D), 32D);
    }

    private static CNG simplex(long seed) {
        return new CNG(new RNG(seed), NoiseType.SIMPLEX, 1D, 1);
    }

    private static double sample(CNG noise, boolean threeDimensions) {
        return threeDimensions ? noise.noiseFastSigned3D(12D, 7D, 19D) : noise.noiseFastSigned2D(12D, 19D);
    }

    private static FloatCache installSnapshot(CNG noise) {
        FloatCache snapshot = new FloatCache(4, 4);
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                snapshot.set(x, z, (float) noise.noise(x, z));
            }
        }
        noise.setCache(snapshot);
        assertSame(snapshot, noise.getCache());
        return snapshot;
    }

    private static final class RecordingNoise implements NoiseGenerator, OctaveNoise {
        private final boolean noScale;
        private int octaves;
        private int calls;
        private double sampledX;
        private double sampledZ;

        private RecordingNoise(boolean noScale) {
            this.noScale = noScale;
        }

        @Override
        public boolean isNoScale() {
            return noScale;
        }

        @Override
        public void setOctaves(int octaves) {
            this.octaves = octaves;
        }

        @Override
        public double noise(double x) {
            return noise(x, 0D);
        }

        @Override
        public double noise(double x, double z) {
            calls++;
            sampledX = x;
            sampledZ = z;
            return x * 0.01D + z * 0.02D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return noise(x, z);
        }
    }
}
