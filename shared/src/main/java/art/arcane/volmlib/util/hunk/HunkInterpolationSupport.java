package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.function.Function3;
import art.arcane.volmlib.util.function.NoiseProvider;
import art.arcane.volmlib.util.function.NoiseProvider3;

import java.util.function.DoubleFunction;
import java.util.function.ToDoubleFunction;

public final class HunkInterpolationSupport {
    private HunkInterpolationSupport() {
    }

    @FunctionalInterface
    public interface NoiseSampler2 {
        double sample(int x, int z, double scale, NoiseProvider provider);
    }

    @FunctionalInterface
    public interface NoiseSampler3 {
        double sample(int x, int y, int z, double scale, NoiseProvider3 provider);
    }

    public static <T, H extends HunkLike<T>> H interpolate3D(HunkLike<T> source,
                                                              double scale,
                                                              Function3<Integer, Integer, Integer, H> builder,
                                                              ToDoubleFunction<T> toDouble,
                                                              DoubleFunction<T> fromDouble,
                                                              NoiseSampler3 sampler) {
        H t = builder.apply((int) (source.getWidth() * scale), (int) (source.getHeight() * scale), (int) (source.getDepth() * scale));
        NoiseProvider3 n3 = (x, y, z) -> toDouble.applyAsDouble(
                t.getRaw((int) (x / scale),
                        (int) (y / scale),
                        (int) (z / scale)));

        for (int i = 0; i < t.getWidth(); i++) {
            for (int j = 0; j < t.getHeight(); j++) {
                for (int k = 0; k < t.getDepth(); k++) {
                    t.setRaw(i, j, k, fromDouble.apply(sampler.sample(i, j, k, scale, n3)));
                }
            }
        }

        return t;
    }

    public static <T, H extends HunkLike<T>> H interpolate2D(HunkLike<T> source,
                                                              double scale,
                                                              Function3<Integer, Integer, Integer, H> builder,
                                                              ToDoubleFunction<T> toDouble,
                                                              DoubleFunction<T> fromDouble,
                                                              NoiseSampler2 sampler) {
        H t = builder.apply((int) (source.getWidth() * scale), 1, (int) (source.getDepth() * scale));
        NoiseProvider n2 = (x, z) -> toDouble.applyAsDouble(
                t.getRaw((int) (x / scale),
                        0,
                        (int) (z / scale)));

        for (int i = 0; i < t.getWidth(); i++) {
            for (int j = 0; j < t.getDepth(); j++) {
                t.setRaw(i, 0, j, fromDouble.apply(sampler.sample(i, j, scale, n2)));
            }
        }

        return t;
    }
}
