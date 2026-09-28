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
    private static final int KEY_CACHE_SHIFT = 5;
    private static final int KEY_CACHE_SPAN = 1 << KEY_CACHE_SHIFT;
    private static final Long[] KEY_CACHE = new Long[KEY_CACHE_SPAN * KEY_CACHE_SPAN];

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

    /**
     * Boxed region key, reused from a small direct-mapped cache so hot region lookups do not allocate.
     * Racing threads may each box their own copy; equal keys stay interchangeable.
     */
    public static Long key(int x, int z) {
        long key = CacheKey.key(x, z);
        int slot = ((x & (KEY_CACHE_SPAN - 1)) << KEY_CACHE_SHIFT) | (z & (KEY_CACHE_SPAN - 1));
        Long cached = KEY_CACHE[slot];
        if (cached != null && cached == key) {
            return cached;
        }
        Long boxed = key;
        KEY_CACHE[slot] = boxed;
        return boxed;
    }
}
