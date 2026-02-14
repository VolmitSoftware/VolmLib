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

package art.arcane.volmlib.util.nbt.mca;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MCAUtilSupport {
    private static final Pattern MCA_FILE_PATTERN = Pattern.compile("^.*r\\.(?<regionX>-?\\d+)\\.(?<regionZ>-?\\d+)\\.mca$");

    private MCAUtilSupport() {
    }

    public static String createNameFromChunkLocation(int chunkX, int chunkZ) {
        return createNameFromRegionLocation(chunkToRegion(chunkX), chunkToRegion(chunkZ));
    }

    public static String createNameFromBlockLocation(int blockX, int blockZ) {
        return createNameFromRegionLocation(blockToRegion(blockX), blockToRegion(blockZ));
    }

    public static String createNameFromRegionLocation(int regionX, int regionZ) {
        return "r." + regionX + "." + regionZ + ".mca";
    }

    public static int blockToChunk(int block) {
        return block >> 4;
    }

    public static int blockToRegion(int block) {
        return block >> 9;
    }

    public static int chunkToRegion(int chunk) {
        return chunk >> 5;
    }

    public static int regionToChunk(int region) {
        return region << 5;
    }

    public static int regionToBlock(int region) {
        return region << 9;
    }

    public static int chunkToBlock(int chunk) {
        return chunk << 4;
    }

    public static int[] parseRegionCoordinates(File file) {
        Matcher matcher = MCA_FILE_PATTERN.matcher(file.getName());
        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid mca file name: " + file.getName());
        }

        return new int[]{
                Integer.parseInt(matcher.group("regionX")),
                Integer.parseInt(matcher.group("regionZ"))
        };
    }
}
