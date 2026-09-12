/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.volmlib.util.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChunkSpiral {
    private ChunkSpiral() {
    }

    public static List<int[]> centerOut(int centerX, int centerZ, int radius) {
        List<int[]> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targets.add(new int[]{centerX + dx, centerZ + dz});
            }
        }

        targets.sort(Comparator.comparingInt((int[] t) -> {
            int ox = t[0] - centerX;
            int oz = t[1] - centerZ;
            return ox * ox + oz * oz;
        }));
        return targets;
    }
}
