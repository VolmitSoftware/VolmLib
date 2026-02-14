package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.function.Consumer6;

public final class HunkSectionSupport {
    private HunkSectionSupport() {
    }

    public static void forEachAtomic2DSectionBounds(HunkLike<?> h, int sections, Consumer6<Integer, Integer, Integer, Integer, Integer, Integer> bounds) {
        int dim = HunkCoreSupport.get2DDimension(sections, HunkCoreSupport.getMinimumDimension(h));

        if (sections <= 1) {
            bounds.accept(0, 0, 0, h.getWidth(), h.getHeight(), h.getDepth());
            return;
        }

        int w = h.getWidth() / dim;
        int wr = h.getWidth() - (w * dim);
        int d = h.getDepth() / dim;
        int dr = h.getDepth() - (d * dim);
        int i;
        int j;

        for (i = 0; i < h.getWidth(); i += w) {
            for (j = 0; j < h.getDepth(); j += d) {
                bounds.accept(i, 0, j, i + w + (i == 0 ? wr : 0), h.getHeight(), j + d + (j == 0 ? dr : 0));
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }
    }

    public static void forEach2DSectionBounds(HunkLike<?> h, int sections, Consumer6<Integer, Integer, Integer, Integer, Integer, Integer> bounds) {
        int dim = HunkCoreSupport.get2DDimension(sections, HunkCoreSupport.getMinimumDimension(h));

        if (sections <= 1) {
            bounds.accept(0, 0, 0, h.getWidth(), h.getHeight(), h.getDepth());
            return;
        }

        int w = h.getWidth() / dim;
        int wr = h.getWidth() - (w * dim);
        int d = h.getDepth() / dim;
        int dr = h.getDepth() - (d * dim);
        int i;
        int j;

        for (i = 0; i < h.getWidth(); i += w) {
            for (j = 0; j < h.getDepth(); j += d) {
                bounds.accept(i, 0, j, i + w + (i == 0 ? wr : 0), h.getHeight(), j + d + (j == 0 ? dr : 0));
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }
    }

    public static void forEach2DYRangeSectionBounds(HunkLike<?> h, int sections, int ymin, int ymax, Consumer6<Integer, Integer, Integer, Integer, Integer, Integer> bounds) {
        int dim = HunkCoreSupport.get2DDimension(sections, HunkCoreSupport.getMinimumDimension(h));

        if (sections <= 1) {
            bounds.accept(0, ymin, 0, h.getWidth(), ymax, h.getDepth());
            return;
        }

        int w = h.getWidth() / dim;
        int wr = h.getWidth() - (w * dim);
        int d = h.getDepth() / dim;
        int dr = h.getDepth() - (d * dim);
        int i;
        int j;

        for (i = 0; i < h.getWidth(); i += w) {
            for (j = 0; j < h.getDepth(); j += d) {
                bounds.accept(i, ymin, j, i + w + (i == 0 ? wr : 0), ymax, j + d + (j == 0 ? dr : 0));
                i = i == 0 ? i + wr : i;
                j = j == 0 ? j + dr : j;
            }
        }
    }

    public static void forEach3DSectionBounds(HunkLike<?> h, int sections, Consumer6<Integer, Integer, Integer, Integer, Integer, Integer> bounds) {
        int dim = HunkCoreSupport.get3DDimension(sections, HunkCoreSupport.getMinimumDimension(h));

        if (sections <= 1) {
            bounds.accept(0, 0, 0, h.getWidth(), h.getHeight(), h.getDepth());
            return;
        }

        int w = h.getWidth() / dim;
        int hh = h.getHeight() / dim;
        int d = h.getDepth() / dim;
        int wr = h.getWidth() - (w * dim);
        int hr = h.getHeight() - (hh * dim);
        int dr = h.getDepth() - (d * dim);
        int i;
        int j;
        int k;

        for (i = 0; i < h.getWidth(); i += w) {
            int ii = i;

            for (j = 0; j < h.getHeight(); j += d) {
                int jj = j;

                for (k = 0; k < h.getDepth(); k += d) {
                    int kk = k;
                    bounds.accept(ii, jj, kk, i + w + (i == 0 ? wr : 0), j + hh + (j == 0 ? hr : 0), k + d + (k == 0 ? dr : 0));
                    i = i == 0 ? i + wr : i;
                    j = j == 0 ? j + hr : j;
                    k = k == 0 ? k + dr : k;
                }
            }
        }
    }
}
