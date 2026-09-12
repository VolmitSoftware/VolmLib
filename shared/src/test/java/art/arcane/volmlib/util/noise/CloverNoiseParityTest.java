package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class CloverNoiseParityTest {
    private static final long[] SEEDS = {0L, -1L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE};
    private static final double[][] POINTS = {
            {0.125D, -0.75D, 1.5D},
            {-1000.25D, 64.5D, 2048.75D},
            {29_999_984D, -64D, -29_999_984D},
            {Math.PI, Math.E, -Math.sqrt(2D)}
    };
    private static final long[][] EXPECTED_3D = {
            {4606295205971726443L, 4596584066878230492L, 4601072533138395306L, 4595465536009596573L},
            {4594559156169257303L, 4602986449390023959L, 4602157576770610768L, 4604264428473675137L},
            {4594287239476896260L, 4606819753250159891L, 4603131747488558437L, 4605050910998320058L},
            {4594574716919961912L, 4582369728393548057L, 4600712766107896080L, 4594781081233153808L},
            {4603622723932858649L, 4604886251804482757L, 4600180200303866897L, 4597047378643079682L}
    };
    private static final long[][] EXPECTED_2D = {
            {4600886466954977930L, 4603042301510796801L, 4605282822283219348L, 4603381024907987886L},
            {4604231235993958026L, 4603294413466771076L, 4606358936184576936L, 4605313173145937690L},
            {4602575096448892092L, 4600034591678710230L, 4603842817538381592L, 4604447254328254894L},
            {4602176302263458666L, 4592022086679349802L, 4605779333798454855L, 4603816542564903403L},
            {4603283241394868042L, 4601437833095430179L, 4606037056899058631L, 4604909992999588843L}
    };

    @Test
    public void optimizedVectorMathPreservesExactNoiseBits() {
        for (int seedIndex = 0; seedIndex < SEEDS.length; seedIndex++) {
            CloverNoise noise = new CloverNoise(SEEDS[seedIndex]);
            for (int pointIndex = 0; pointIndex < POINTS.length; pointIndex++) {
                double[] point = POINTS[pointIndex];
                assertEquals(EXPECTED_3D[seedIndex][pointIndex], Double.doubleToRawLongBits(
                        noise.noise(point[0], point[1], point[2])));
                assertEquals(EXPECTED_2D[seedIndex][pointIndex], Double.doubleToRawLongBits(
                        noise.noise(point[0], point[2])));
            }
        }
    }

    @Test
    public void threeDimensionalCoordinateGridPreservesExactNoiseBits() {
        long[] expected = {-4534805072329300766L, 938556028071277557L, -6813256383186365522L,
                -4452859942568220233L, 5365194784097108235L};

        for (int index = 0; index < SEEDS.length; index++) {
            assertEquals("seed=" + SEEDS[index], expected[index], coordinateDigest(new CloverNoise(SEEDS[index]), 0));
        }
    }

    @Test(timeout = 15000)
    public void concurrentSamplingKeepsThreadScratchIndependent() throws Exception {
        CloverNoise shared = new CloverNoise(1337L);
        long[] expected = new long[8];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = coordinateDigest(shared, index * 991);
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Long>> results = new ArrayList<>(expected.length);
            for (int index = 0; index < expected.length; index++) {
                int start = index * 991;
                results.add(executor.submit(() -> coordinateDigest(shared, start)));
            }

            for (int index = 0; index < expected.length; index++) {
                assertEquals(expected[index], results.get(index).get(5, TimeUnit.SECONDS).longValue());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void samplingKeepsCallerVectorsUnchanged() {
        CloverNoise.Noise3D noise = new CloverNoise.Noise3D(1337L);
        CloverNoise.Vector3 point = new CloverNoise.Vector3(0.25D, 0.5D, -0.75D);
        double expected = noise.noise(point);
        noise.noise(71D, -31D, 113D);

        assertEquals(0.25D, point.getX(), 0D);
        assertEquals(0.5D, point.getY(), 0D);
        assertEquals(-0.75D, point.getZ(), 0D);
        assertEquals(expected, noise.noise(point), 0D);
    }

    private static long coordinateDigest(CloverNoise noise, int start) {
        long digest = 0L;
        for (int index = start; index < start + 8192; index++) {
            double x = ((index * 1580030173L) % 60000000L - 30000000L) / 64D + 0.371D;
            double y = ((index * 91437L) % 384L - 64L) / 64D + 0.231D;
            double z = ((index * 59260789L) % 60000000L - 30000000L) / 64D - 0.219D;
            digest = Long.rotateLeft(digest, 7) ^ Double.doubleToRawLongBits(noise.noise(x, y, z));
        }
        return digest;
    }
}
