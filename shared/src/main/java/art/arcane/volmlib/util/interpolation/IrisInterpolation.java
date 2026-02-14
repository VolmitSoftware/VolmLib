package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.function.NoiseProvider;

public class IrisInterpolation {
    public static double lerp(double a, double b, double f) {
        return a + (f * (b - a));
    }

    public static double blerp(double a, double b, double c, double d, double tx, double ty) {
        return lerp(lerp(a, b, tx), lerp(c, d, tx), ty);
    }

    public static double trilerp(double v1, double v2, double v3, double v4, double v5, double v6, double v7, double v8, double x, double y, double z) {
        return lerp(blerp(v1, v2, v3, v4, x, y), blerp(v5, v6, v7, v8, x, y), z);
    }

    public static double rangeScale(double amin, double amax, double bmin, double bmax, double b) {
        if (bmax == bmin) {
            return amin;
        }

        return amin + ((amax - amin) * ((b - bmin) / (bmax - bmin)));
    }

    public static double cubic(double p0, double p1, double p2, double p3, double mu) {
        double mu2 = mu * mu;
        double a0 = p3 - p2 - p0 + p1;
        double a1 = p0 - p1 - a0;
        double a2 = p2 - p0;
        double a3 = p1;
        return a0 * mu * mu2 + a1 * mu2 + a2 * mu + a3;
    }

    public static double hermite(double p0, double p1, double p2, double p3, double mu, double tension, double bias) {
        double mu2 = mu * mu;
        double mu3 = mu2 * mu;
        double m0 = (p1 - p0) * (1 + bias) * (1 - tension) / 2D;
        m0 += (p2 - p1) * (1 - bias) * (1 - tension) / 2D;
        double m1 = (p2 - p1) * (1 + bias) * (1 - tension) / 2D;
        m1 += (p3 - p2) * (1 - bias) * (1 - tension) / 2D;
        double a0 = 2 * mu3 - 3 * mu2 + 1;
        double a1 = mu3 - 2 * mu2 + mu;
        double a2 = mu3 - mu2;
        double a3 = -2 * mu3 + 3 * mu2;
        return (a0 * p1) + (a1 * m0) + (a2 * m1) + (a3 * p2);
    }

    public static double bicubic(
            double p00, double p01, double p02, double p03,
            double p10, double p11, double p12, double p13,
            double p20, double p21, double p22, double p23,
            double p30, double p31, double p32, double p33,
            double mux, double muy
    ) {
        return cubic(
                cubic(p00, p01, p02, p03, muy),
                cubic(p10, p11, p12, p13, muy),
                cubic(p20, p21, p22, p23, muy),
                cubic(p30, p31, p32, p33, muy),
                mux
        );
    }

    public static double bihermite(
            double p00, double p01, double p02, double p03,
            double p10, double p11, double p12, double p13,
            double p20, double p21, double p22, double p23,
            double p30, double p31, double p32, double p33,
            double mux, double muy, double tension, double bias
    ) {
        return hermite(
                hermite(p00, p01, p02, p03, muy, tension, bias),
                hermite(p10, p11, p12, p13, muy, tension, bias),
                hermite(p20, p21, p22, p23, muy, tension, bias),
                hermite(p30, p31, p32, p33, muy, tension, bias),
                mux, tension, bias
        );
    }

    public static double tricubic(
            double p000, double p001, double p002, double p003,
            double p010, double p011, double p012, double p013,
            double p020, double p021, double p022, double p023,
            double p030, double p031, double p032, double p033,
            double p100, double p101, double p102, double p103,
            double p110, double p111, double p112, double p113,
            double p120, double p121, double p122, double p123,
            double p130, double p131, double p132, double p133,
            double p200, double p201, double p202, double p203,
            double p210, double p211, double p212, double p213,
            double p220, double p221, double p222, double p223,
            double p230, double p231, double p232, double p233,
            double p300, double p301, double p302, double p303,
            double p310, double p311, double p312, double p313,
            double p320, double p321, double p322, double p323,
            double p330, double p331, double p332, double p333,
            double mux, double muy, double muz
    ) {
        return cubic(
                bicubic(p000, p001, p002, p003, p010, p011, p012, p013, p020, p021, p022, p023, p030, p031, p032, p033, mux, muy),
                bicubic(p100, p101, p102, p103, p110, p111, p112, p113, p120, p121, p122, p123, p130, p131, p132, p133, mux, muy),
                bicubic(p200, p201, p202, p203, p210, p211, p212, p213, p220, p221, p222, p223, p230, p231, p232, p233, mux, muy),
                bicubic(p300, p301, p302, p303, p310, p311, p312, p313, p320, p321, p322, p323, p330, p331, p332, p333, mux, muy),
                muz
        );
    }

    public static double trihermite(
            double p000, double p001, double p002, double p003,
            double p010, double p011, double p012, double p013,
            double p020, double p021, double p022, double p023,
            double p030, double p031, double p032, double p033,
            double p100, double p101, double p102, double p103,
            double p110, double p111, double p112, double p113,
            double p120, double p121, double p122, double p123,
            double p130, double p131, double p132, double p133,
            double p200, double p201, double p202, double p203,
            double p210, double p211, double p212, double p213,
            double p220, double p221, double p222, double p223,
            double p230, double p231, double p232, double p233,
            double p300, double p301, double p302, double p303,
            double p310, double p311, double p312, double p313,
            double p320, double p321, double p322, double p323,
            double p330, double p331, double p332, double p333,
            double mux, double muy, double muz, double tension, double bias
    ) {
        return hermite(
                bihermite(p000, p001, p002, p003, p010, p011, p012, p013, p020, p021, p022, p023, p030, p031, p032, p033, mux, muy, tension, bias),
                bihermite(p100, p101, p102, p103, p110, p111, p112, p113, p120, p121, p122, p123, p130, p131, p132, p133, mux, muy, tension, bias),
                bihermite(p200, p201, p202, p203, p210, p211, p212, p213, p220, p221, p222, p223, p230, p231, p232, p233, mux, muy, tension, bias),
                bihermite(p300, p301, p302, p303, p310, p311, p312, p313, p320, p321, p322, p323, p330, p331, p332, p333, mux, muy, tension, bias),
                muz, tension, bias
        );
    }

    public static double getNoise(InterpolationMethod method, int x, int z, double radius, NoiseProvider provider) {
        switch (method) {
            case NONE:
                return provider.noise(x, z);
            case STARCAST_3:
                return Starcast.starcast(x, z, radius, 3, provider);
            case STARCAST_6:
                return Starcast.starcast(x, z, radius, 6, provider);
            case STARCAST_9:
                return Starcast.starcast(x, z, radius, 9, provider);
            case STARCAST_12:
                return Starcast.starcast(x, z, radius, 12, provider);
            case BILINEAR_STARCAST_3:
                return (blerpNoise(x, z, radius, provider) + Starcast.starcast(x, z, radius, 3, provider)) / 2D;
            case BILINEAR_STARCAST_6:
                return (blerpNoise(x, z, radius, provider) + Starcast.starcast(x, z, radius, 6, provider)) / 2D;
            case BILINEAR_STARCAST_9:
                return (blerpNoise(x, z, radius, provider) + Starcast.starcast(x, z, radius, 9, provider)) / 2D;
            case BILINEAR_STARCAST_12:
                return (blerpNoise(x, z, radius, provider) + Starcast.starcast(x, z, radius, 12, provider)) / 2D;
            case HERMITE_STARCAST_3:
            case HERMITE_STARCAST_6:
            case HERMITE_STARCAST_9:
            case HERMITE_STARCAST_12:
                return (bihermiteNoise(x, z, radius, provider) + blerpNoise(x, z, radius, provider)) / 2D;
            case BICUBIC:
                return bicubicNoise(x, z, radius, provider);
            case HERMITE:
            case CATMULL_ROM_SPLINE:
            case HERMITE_TENSE:
            case HERMITE_LOOSE:
            case HERMITE_LOOSE_HALF_POSITIVE_BIAS:
            case HERMITE_LOOSE_HALF_NEGATIVE_BIAS:
            case HERMITE_LOOSE_FULL_POSITIVE_BIAS:
            case HERMITE_LOOSE_FULL_NEGATIVE_BIAS:
                return bihermiteNoise(x, z, radius, provider);
            case BILINEAR:
            case BILINEAR_BEZIER:
            case BILINEAR_PARAMETRIC_2:
            case BILINEAR_PARAMETRIC_4:
            case BILINEAR_PARAMETRIC_1_5:
            default:
                return blerpNoise(x, z, radius, provider);
        }
    }

    private static double blerpNoise(int x, int z, double radius, NoiseProvider provider) {
        double scaledX = x / radius;
        double scaledZ = z / radius;
        int x1 = (int) Math.floor(scaledX);
        int z1 = (int) Math.floor(scaledZ);
        int x2 = x1 + 1;
        int z2 = z1 + 1;
        double tx = scaledX - x1;
        double tz = scaledZ - z1;
        return blerp(
                provider.noise(x1, z1),
                provider.noise(x2, z1),
                provider.noise(x1, z2),
                provider.noise(x2, z2),
                tx, tz
        );
    }

    private static double bicubicNoise(int x, int z, double radius, NoiseProvider provider) {
        double scaledX = x / radius;
        double scaledZ = z / radius;
        int xi = (int) Math.floor(scaledX);
        int zi = (int) Math.floor(scaledZ);
        double tx = scaledX - xi;
        double tz = scaledZ - zi;
        return bicubic(
                provider.noise(xi - 1, zi - 1), provider.noise(xi - 1, zi), provider.noise(xi - 1, zi + 1), provider.noise(xi - 1, zi + 2),
                provider.noise(xi, zi - 1), provider.noise(xi, zi), provider.noise(xi, zi + 1), provider.noise(xi, zi + 2),
                provider.noise(xi + 1, zi - 1), provider.noise(xi + 1, zi), provider.noise(xi + 1, zi + 1), provider.noise(xi + 1, zi + 2),
                provider.noise(xi + 2, zi - 1), provider.noise(xi + 2, zi), provider.noise(xi + 2, zi + 1), provider.noise(xi + 2, zi + 2),
                tx, tz
        );
    }

    private static double bihermiteNoise(int x, int z, double radius, NoiseProvider provider) {
        double scaledX = x / radius;
        double scaledZ = z / radius;
        int xi = (int) Math.floor(scaledX);
        int zi = (int) Math.floor(scaledZ);
        double tx = scaledX - xi;
        double tz = scaledZ - zi;
        return bihermite(
                provider.noise(xi - 1, zi - 1), provider.noise(xi - 1, zi), provider.noise(xi - 1, zi + 1), provider.noise(xi - 1, zi + 2),
                provider.noise(xi, zi - 1), provider.noise(xi, zi), provider.noise(xi, zi + 1), provider.noise(xi, zi + 2),
                provider.noise(xi + 1, zi - 1), provider.noise(xi + 1, zi), provider.noise(xi + 1, zi + 1), provider.noise(xi + 1, zi + 2),
                provider.noise(xi + 2, zi - 1), provider.noise(xi + 2, zi), provider.noise(xi + 2, zi + 1), provider.noise(xi + 2, zi + 2),
                tx, tz, 0D, 0D
        );
    }
}
