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

package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.cache.CacheKey;

import java.io.File;

public final class MantleRegionFiles {
    private MantleRegionFiles() {
    }

    public static File fileForRegion(File folder, int x, int z) {
        return fileForRegion(folder, key(x, z), true);
    }

    public static File fileForRegion(File folder, Long key, boolean convert) {
        File old = oldFileForRegion(folder, key);
        File modern = new File(folder, "pv." + key + ".ttp.lz4b");
        if (old.exists() && !modern.exists() && convert) {
            return old;
        }

        File parent = modern.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        return modern;
    }

    public static File oldFileForRegion(File folder, Long key) {
        return new File(folder, "p." + key + ".ttp.lz4b");
    }

    public static Long key(int x, int z) {
        return CacheKey.key(x, z);
    }
}
