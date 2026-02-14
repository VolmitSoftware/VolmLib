package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.function.Function3;

public final class HunkCoreSupport {
    private HunkCoreSupport() {
    }

    public static int getMaximumDimension(HunkLike<?> h) {
        return Math.max(h.getWidth(), Math.max(h.getHeight(), h.getDepth()));
    }

    public static int getMinimumDimension(HunkLike<?> h) {
        return Math.min(h.getWidth(), Math.min(h.getHeight(), h.getDepth()));
    }

    public static int getMax2DParallelism(HunkLike<?> h) {
        return (int) Math.pow(getMinimumDimension(h) / 2f, 2);
    }

    public static int getMax3DParallelism(HunkLike<?> h) {
        return (int) Math.pow(getMinimumDimension(h) / 2f, 3);
    }

    public static int filterDimension(int dim, int minimumDimension) {
        if (dim <= 1) {
            return 1;
        }

        dim = dim % 2 != 0 ? dim + 1 : dim;

        if (dim > minimumDimension / 2) {
            if (dim <= 2) {
                return 1;
            }

            dim -= 2;
        }

        return dim;
    }

    public static int get2DDimension(int sections, int minimumDimension) {
        if (sections <= 1) {
            return 1;
        }

        return filterDimension((int) Math.ceil(Math.sqrt(sections)), minimumDimension);
    }

    public static int get3DDimension(int sections, int minimumDimension) {
        if (sections <= 1) {
            return 1;
        }

        return filterDimension((int) Math.ceil(Math.cbrt(sections)), minimumDimension);
    }

    public static int[] rotatedBoundsSize(int w, int h, int d, double x, double y, double z) {
        int[] iii = {0, 0, 0};
        int[] aaa = {w, h, d};
        int[] aai = {w, h, 0};
        int[] iaa = {0, h, d};
        int[] aia = {w, 0, d};
        int[] iai = {0, h, 0};
        int[] iia = {0, 0, d};
        int[] aii = {w, 0, 0};
        rotate(x, y, z, iii);
        rotate(x, y, z, aaa);
        rotate(x, y, z, aai);
        rotate(x, y, z, iaa);
        rotate(x, y, z, aia);
        rotate(x, y, z, iai);
        rotate(x, y, z, iia);
        rotate(x, y, z, aii);
        int maxX = max8(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int minX = min8(iii[0], aaa[0], aai[0], iaa[0], aia[0], iai[0], iia[0], aii[0]);
        int maxY = max8(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int minY = min8(iii[1], aaa[1], aai[1], iaa[1], aia[1], iai[1], iia[1], aii[1]);
        int maxZ = max8(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        int minZ = min8(iii[2], aaa[2], aai[2], iaa[2], aia[2], iai[2], iia[2], aii[2]);
        return new int[]{maxX - minX, maxY - minY, maxZ - minZ};
    }

    public static int max8(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return Math.max(Math.max(Math.max(a5, a6), Math.max(a7, a8)), Math.max(Math.max(a1, a2), Math.max(a3, a4)));
    }

    public static int min8(int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return Math.min(Math.min(Math.min(a5, a6), Math.min(a7, a8)), Math.min(Math.min(a1, a2), Math.min(a3, a4)));
    }

    public static void rotate(double x, double y, double z, int[] c) {
        if (x % 360 != 0) {
            rotateAroundX(Math.toRadians(x), c);
        }

        if (y % 360 != 0) {
            rotateAroundY(Math.toRadians(y), c);
        }

        if (z % 360 != 0) {
            rotateAroundZ(Math.toRadians(z), c);
        }
    }

    public static void rotateAroundX(double a, int[] c) {
        rotateAroundX(Math.cos(a), Math.sin(a), c);
    }

    public static void rotateAroundX(double cos, double sin, int[] c) {
        int y = (int) Math.floor(cos * (double) (c[1] + 0.5) - sin * (double) (c[2] + 0.5));
        int z = (int) Math.floor(sin * (double) (c[1] + 0.5) + cos * (double) (c[2] + 0.5));
        c[1] = y;
        c[2] = z;
    }

    public static void rotateAroundY(double a, int[] c) {
        rotateAroundY(Math.cos(a), Math.sin(a), c);
    }

    public static void rotateAroundY(double cos, double sin, int[] c) {
        int x = (int) Math.floor(cos * (double) (c[0] + 0.5) + sin * (double) (c[2] + 0.5));
        int z = (int) Math.floor(-sin * (double) (c[0] + 0.5) + cos * (double) (c[2] + 0.5));
        c[0] = x;
        c[2] = z;
    }

    public static void rotateAroundZ(double a, int[] c) {
        rotateAroundZ(Math.cos(a), Math.sin(a), c);
    }

    public static void rotateAroundZ(double cos, double sin, int[] c) {
        int x = (int) Math.floor(cos * (double) (c[0] + 0.5) - sin * (double) (c[1] + 0.5));
        int y = (int) Math.floor(sin * (double) (c[0] + 0.5) + cos * (double) (c[1] + 0.5));
        c[0] = x;
        c[1] = y;
    }

    public static boolean contains(HunkLike<?> h, int x, int y, int z) {
        return x < h.getWidth() && x >= 0 && y < h.getHeight() && y >= 0 && z < h.getDepth() && z >= 0;
    }

    public static int volume(HunkLike<?> h) {
        return h.getWidth() * h.getDepth() * h.getHeight();
    }

    public static <T, H extends HunkLike<T>> H rotate(HunkLike<T> source, double x, double y, double z, Function3<Integer, Integer, Integer, H> builder) {
        int w = source.getWidth();
        int h = source.getHeight();
        int d = source.getDepth();
        int i;
        int j;
        int k;
        int[] c = {w / 2, h / 2, d / 2};
        int[] b = {0, 0, 0};
        int[] bounds = rotatedBoundsSize(w, h, d, x, y, z);
        H rotated = builder.apply(bounds[0], bounds[1], bounds[2]);
        int[] cr = {bounds[0] / 2, bounds[1] / 2, bounds[2] / 2};

        for (i = 0; i < w; i++) {
            for (j = 0; j < h; j++) {
                for (k = 0; k < d; k++) {
                    b[0] = i - c[0];
                    b[1] = j - c[1];
                    b[2] = k - c[2];
                    rotate(x, y, z, b);

                    try {
                        rotated.setRaw(b[0] + cr[0], b[1] + cr[1], b[2] + cr[2], source.getRaw(i, j, k));
                    } catch (Throwable e) {
                    }
                }
            }
        }

        return rotated;
    }
}
