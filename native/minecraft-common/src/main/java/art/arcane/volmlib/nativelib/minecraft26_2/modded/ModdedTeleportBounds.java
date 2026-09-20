/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

final class ModdedTeleportBounds {
    private ModdedTeleportBounds() {
    }

    static int blockCoordinate(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    static int clampY(int minY, int maxY, int requestedY) {
        int minimumY = minimumY(minY, maxY);
        int maximumY = maximumY(minY, maxY);
        return Math.max(minimumY, Math.min(maximumY, requestedY));
    }

    static int minimumY(int minY, int maxY) {
        requireStandingSpace(minY, maxY);
        return minY + 1;
    }

    static int maximumY(int minY, int maxY) {
        requireStandingSpace(minY, maxY);
        return maxY - 2;
    }

    private static void requireStandingSpace(int minY, int maxY) {
        if (maxY - minY < 3) {
            throw new IllegalArgumentException("Level height must provide support, feet, and head space.");
        }
    }
}
