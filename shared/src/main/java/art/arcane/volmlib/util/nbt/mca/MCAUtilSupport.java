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

import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

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
        return PowerOfTwoCoordinates.blockToChunkFloor(block);
    }

    public static int blockToRegion(int block) {
        return PowerOfTwoCoordinates.blockToRegionFloor(block);
    }

    public static int chunkToRegion(int chunk) {
        return PowerOfTwoCoordinates.chunkToRegion(chunk);
    }

    public static int regionToChunk(int region) {
        return PowerOfTwoCoordinates.regionToChunk(region);
    }

    public static int regionToBlock(int region) {
        return PowerOfTwoCoordinates.regionToBlock(region);
    }

    public static int chunkToBlock(int chunk) {
        return PowerOfTwoCoordinates.chunkToBlock(chunk);
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
