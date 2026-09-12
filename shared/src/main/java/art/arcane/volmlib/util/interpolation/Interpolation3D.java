/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.interpolation;

import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.function.NoiseProvider3;
import art.arcane.volmlib.util.interpolation.Starcast;

/**
 * Volumetric field samplers. The scalar kernels these compose (trilerp, tricubic, trihermite) live
 * in {@link IrisInterpolation}; this class only owns the 3D sampling grids and the
 * {@link InterpolationMethod3D} dispatch.
 */
public final class Interpolation3D {
    private Interpolation3D() {
    }

    public static double getStarcast3D(int x, int y, int z, double rad, double checks, NoiseProvider3 n) {
        return (Starcast.starcast(x, z, rad, checks, (xx, zz) -> n.noise(xx, y, zz))
                + Starcast.starcast(x, y, rad, checks, (xx, yy) -> n.noise(xx, yy, z))
                + Starcast.starcast(y, z, rad, checks, (yy, zz) -> n.noise(x, yy, zz))) / 3D;
    }

    public static double getTrilinear(int x, int y, int z, double rad, NoiseProvider3 n) {
        return getTrilinear(x, y, z, rad, rad, rad, n);
    }

    public static double getTrilinear(int x, int y, int z, double radx, double rady, double radz, NoiseProvider3 n) {
        int fx = IrisInterpolation.getRadiusFactor(x, radx);
        int fy = IrisInterpolation.getRadiusFactor(y, rady);
        int fz = IrisInterpolation.getRadiusFactor(z, radz);
        int x1 = (int) Math.round(fx * radx);
        int y1 = (int) Math.round(fy * rady);
        int z1 = (int) Math.round(fz * radz);
        int x2 = (int) Math.round((fx + 1) * radx);
        int y2 = (int) Math.round((fy + 1) * rady);
        int z2 = (int) Math.round((fz + 1) * radz);
        double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
        double py = IrisInterpolation.rangeScale(0, 1, y1, y2, y);
        double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, z);
        //@builder
        return IrisInterpolation.trilerp(
                n.noise(x1, y1, z1),
                n.noise(x2, y1, z1),
                n.noise(x1, y2, z1),
                n.noise(x2, y2, z1),
                n.noise(x1, y1, z2),
                n.noise(x2, y1, z2),
                n.noise(x1, y2, z2),
                n.noise(x2, y2, z2),
                px, py, pz);
        //@done
    }

    public static double getTricubic(int x, int y, int z, double rad, NoiseProvider3 n) {
        return getTricubic(x, y, z, rad, rad, rad, n);
    }

    public static double getTricubic(int x, int y, int z, double radx, double rady, double radz, NoiseProvider3 n) {
        int fx = IrisInterpolation.getRadiusFactor(x, radx);
        int fy = IrisInterpolation.getRadiusFactor(y, rady);
        int fz = IrisInterpolation.getRadiusFactor(z, radz);
        int x0 = (int) Math.round((fx - 1) * radx);
        int y0 = (int) Math.round((fy - 1) * rady);
        int z0 = (int) Math.round((fz - 1) * radz);
        int x1 = (int) Math.round(fx * radx);
        int y1 = (int) Math.round(fy * rady);
        int z1 = (int) Math.round(fz * radz);
        int x2 = (int) Math.round((fx + 1) * radx);
        int y2 = (int) Math.round((fy + 1) * rady);
        int z2 = (int) Math.round((fz + 1) * radz);
        int x3 = (int) Math.round((fx + 2) * radx);
        int y3 = (int) Math.round((fy + 2) * rady);
        int z3 = (int) Math.round((fz + 2) * radz);
        double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
        double py = IrisInterpolation.rangeScale(0, 1, y1, y2, y);
        double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, z);
        //@builder
        //!!!!!!!!!!!!!!!!!! 2 1 3

        return IrisInterpolation.tricubic(
                n.noise(x0, y0, z0),
                n.noise(x0, y1, z0),
                n.noise(x0, y2, z0),
                n.noise(x0, y3, z0),
                n.noise(x1, y0, z0),
                n.noise(x1, y1, z0),
                n.noise(x1, y2, z0),
                n.noise(x1, y3, z0),
                n.noise(x2, y0, z0),
                n.noise(x2, y1, z0),
                n.noise(x2, y2, z0),
                n.noise(x2, y3, z0),
                n.noise(x3, y0, z0),
                n.noise(x3, y1, z0),
                n.noise(x3, y2, z0),
                n.noise(x3, y3, z0),
                n.noise(x0, y0, z1),
                n.noise(x0, y1, z1),
                n.noise(x0, y2, z1),
                n.noise(x0, y3, z1),
                n.noise(x1, y0, z1),
                n.noise(x1, y1, z1),
                n.noise(x1, y2, z1),
                n.noise(x1, y3, z1),
                n.noise(x2, y0, z1),
                n.noise(x2, y1, z1),
                n.noise(x2, y2, z1),
                n.noise(x2, y3, z1),
                n.noise(x3, y0, z1),
                n.noise(x3, y1, z1),
                n.noise(x3, y2, z1),
                n.noise(x3, y3, z1),
                n.noise(x0, y0, z2),
                n.noise(x0, y1, z2),
                n.noise(x0, y2, z2),
                n.noise(x0, y3, z2),
                n.noise(x1, y0, z2),
                n.noise(x1, y1, z2),
                n.noise(x1, y2, z2),
                n.noise(x1, y3, z2),
                n.noise(x2, y0, z2),
                n.noise(x2, y1, z2),
                n.noise(x2, y2, z2),
                n.noise(x2, y3, z2),
                n.noise(x3, y0, z2),
                n.noise(x3, y1, z2),
                n.noise(x3, y2, z2),
                n.noise(x3, y3, z2),
                n.noise(x0, y0, z3),
                n.noise(x0, y1, z3),
                n.noise(x0, y2, z3),
                n.noise(x0, y3, z3),
                n.noise(x1, y0, z3),
                n.noise(x1, y1, z3),
                n.noise(x1, y2, z3),
                n.noise(x1, y3, z3),
                n.noise(x2, y0, z3),
                n.noise(x2, y1, z3),
                n.noise(x2, y2, z3),
                n.noise(x2, y3, z3),
                n.noise(x3, y0, z3),
                n.noise(x3, y1, z3),
                n.noise(x3, y2, z3),
                n.noise(x3, y3, z3),
                px, py, pz);
        //@done
    }

    public static double getTrihermite(int x, int y, int z, double rad, NoiseProvider3 n, double tension, double bias) {
        return getTrihermite(x, y, z, rad, rad, rad, n, tension, bias);
    }

    public static double getTrihermite(int x, int y, int z, double radx, double rady, double radz, NoiseProvider3 n) {
        return getTrihermite(x, y, z, radx, rady, radz, n, 0D, 0D);
    }

    public static double getTrihermite(int x, int y, int z, double radx, double rady, double radz, NoiseProvider3 n, double tension, double bias) {
        int fx = IrisInterpolation.getRadiusFactor(x, radx);
        int fy = IrisInterpolation.getRadiusFactor(y, rady);
        int fz = IrisInterpolation.getRadiusFactor(z, radz);
        int x0 = (int) Math.round((fx - 1) * radx);
        int y0 = (int) Math.round((fy - 1) * rady);
        int z0 = (int) Math.round((fz - 1) * radz);
        int x1 = (int) Math.round(fx * radx);
        int y1 = (int) Math.round(fy * rady);
        int z1 = (int) Math.round(fz * radz);
        int x2 = (int) Math.round((fx + 1) * radx);
        int y2 = (int) Math.round((fy + 1) * rady);
        int z2 = (int) Math.round((fz + 1) * radz);
        int x3 = (int) Math.round((fx + 2) * radx);
        int y3 = (int) Math.round((fy + 2) * rady);
        int z3 = (int) Math.round((fz + 2) * radz);
        double px = IrisInterpolation.rangeScale(0, 1, x1, x2, x);
        double py = IrisInterpolation.rangeScale(0, 1, y1, y2, y);
        double pz = IrisInterpolation.rangeScale(0, 1, z1, z2, z);
        //@builder
        //!!!!!!!!!!!!!!!!!! 2 1 3

        return IrisInterpolation.trihermite(
                n.noise(x0, y0, z0),
                n.noise(x0, y1, z0),
                n.noise(x0, y2, z0),
                n.noise(x0, y3, z0),
                n.noise(x1, y0, z0),
                n.noise(x1, y1, z0),
                n.noise(x1, y2, z0),
                n.noise(x1, y3, z0),
                n.noise(x2, y0, z0),
                n.noise(x2, y1, z0),
                n.noise(x2, y2, z0),
                n.noise(x2, y3, z0),
                n.noise(x3, y0, z0),
                n.noise(x3, y1, z0),
                n.noise(x3, y2, z0),
                n.noise(x3, y3, z0),
                n.noise(x0, y0, z1),
                n.noise(x0, y1, z1),
                n.noise(x0, y2, z1),
                n.noise(x0, y3, z1),
                n.noise(x1, y0, z1),
                n.noise(x1, y1, z1),
                n.noise(x1, y2, z1),
                n.noise(x1, y3, z1),
                n.noise(x2, y0, z1),
                n.noise(x2, y1, z1),
                n.noise(x2, y2, z1),
                n.noise(x2, y3, z1),
                n.noise(x3, y0, z1),
                n.noise(x3, y1, z1),
                n.noise(x3, y2, z1),
                n.noise(x3, y3, z1),
                n.noise(x0, y0, z2),
                n.noise(x0, y1, z2),
                n.noise(x0, y2, z2),
                n.noise(x0, y3, z2),
                n.noise(x1, y0, z2),
                n.noise(x1, y1, z2),
                n.noise(x1, y2, z2),
                n.noise(x1, y3, z2),
                n.noise(x2, y0, z2),
                n.noise(x2, y1, z2),
                n.noise(x2, y2, z2),
                n.noise(x2, y3, z2),
                n.noise(x3, y0, z2),
                n.noise(x3, y1, z2),
                n.noise(x3, y2, z2),
                n.noise(x3, y3, z2),
                n.noise(x0, y0, z3),
                n.noise(x0, y1, z3),
                n.noise(x0, y2, z3),
                n.noise(x0, y3, z3),
                n.noise(x1, y0, z3),
                n.noise(x1, y1, z3),
                n.noise(x1, y2, z3),
                n.noise(x1, y3, z3),
                n.noise(x2, y0, z3),
                n.noise(x2, y1, z3),
                n.noise(x2, y2, z3),
                n.noise(x2, y3, z3),
                n.noise(x3, y0, z3),
                n.noise(x3, y1, z3),
                n.noise(x3, y2, z3),
                n.noise(x3, y3, z3),
                px, py, pz, tension, bias);
        //@done
    }

    public static double getNoise3D(InterpolationMethod3D method, int x, int y, int z, double radx, double rady, double radz, NoiseProvider3 n) {
        return switch (method) {
            case TRILINEAR -> getTrilinear(x, y, z, radx, rady, radz, n);
            case TRICUBIC -> getTricubic(x, y, z, radx, rady, radz, n);
            case TRIHERMITE -> getTrihermite(x, y, z, radx, rady, radz, n);
            case TRISTARCAST_3 -> getStarcast3D(x, y, z, radx, 3D, n);
            case TRISTARCAST_6 -> getStarcast3D(x, y, z, radx, 6D, n);
            case TRISTARCAST_9 -> getStarcast3D(x, y, z, radx, 9D, n);
            case TRISTARCAST_12 -> getStarcast3D(x, y, z, radx, 12D, n);
            case TRILINEAR_TRISTARCAST_3 ->
                    getStarcast3D(x, y, z, radx, 3D, (xx, yy, zz) -> getTrilinear((int) xx, (int) yy, (int) zz, radx, rady, radz, n));
            case TRILINEAR_TRISTARCAST_6 ->
                    getStarcast3D(x, y, z, radx, 6D, (xx, yy, zz) -> getTrilinear((int) xx, (int) yy, (int) zz, radx, rady, radz, n));
            case TRILINEAR_TRISTARCAST_9 ->
                    getStarcast3D(x, y, z, radx, 9D, (xx, yy, zz) -> getTrilinear((int) xx, (int) yy, (int) zz, radx, rady, radz, n));
            case TRILINEAR_TRISTARCAST_12 ->
                    getStarcast3D(x, y, z, radx, 12D, (xx, yy, zz) -> getTrilinear((int) xx, (int) yy, (int) zz, radx, rady, radz, n));
            case NONE -> n.noise(x, y, z);
        };
    }

    public static Hunk<Double> getNoise3D(InterpolationMethod3D method, int xo, int yo, int zo, int w, int h, int d, double rad, NoiseProvider3 n) {
        return getNoise3D(method, xo, yo, zo, w, h, d, rad, rad, rad, n);
    }

    /**
     * Get the interpolated 3D noise within a given cuboid size with offsets
     *
     * @param method the interpolation method to use
     * @param xo     the x offset for noise
     * @param yo     the y offset for noise
     * @param zo     the z offset for noise
     * @param w      the width of the result
     * @param h      the height of the result
     * @param d      the depth of the result
     * @param radX   the interpolation radius for the x axis
     * @param radY   the interpolation radius for the y axis
     * @param radZ   the interpolation radius for the z axis
     * @param n      the noise provider
     * @return the resulting hunk of noise
     */
    public static Hunk<Double> getNoise3D(InterpolationMethod3D method, int xo, int yo, int zo, int w, int h, int d, double radX, double radY, double radZ, NoiseProvider3 n) {
        Hunk<Double> hunk = Hunk.newAtomicDoubleHunk(w, h, d);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                for (int k = 0; k < d; k++) {
                    hunk.set(i, j, k, getNoise3D(method, i + xo, j + yo, k + zo, radX, radY, radZ, n));
                }
            }
        }

        return hunk;
    }

    public static double getNoise3D(InterpolationMethod3D method, int x, int y, int z, double rad, NoiseProvider3 n) {
        return getNoise3D(method, x, y, z, rad, rad, rad, n);
    }
}
