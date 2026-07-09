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

package art.arcane.volmlib.util.data;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.inventorygui.RandomColor.Color;
import art.arcane.volmlib.util.inventorygui.RandomColor.Luminosity;
import art.arcane.volmlib.util.inventorygui.RandomColor.SaturationType;

public class VanillaBiomeColors {
    private static final KMap<String, Integer> BIOME_HEX = new KMap<>();
    private static final KMap<String, Color> BIOME_COLOR = new KMap<>();
    private static final KMap<String, Luminosity> BIOME_LUMINOSITY = new KMap<>();
    private static final KMap<String, SaturationType> BIOME_SATURATION = new KMap<>();

    static {
        add("minecraft:ocean", 0x000070, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:plains", 0x8DB360, Color.GREEN, Luminosity.LIGHT, SaturationType.MEDIUM);
        add("minecraft:desert", 0xFA9418, Color.YELLOW, Luminosity.LIGHT, SaturationType.MEDIUM);
        add("minecraft:windswept_hills", 0x606060, Color.MONOCHROME, Luminosity.BRIGHT, null);
        add("minecraft:forest", 0x056621, Color.GREEN, Luminosity.BRIGHT, null);
        add("minecraft:taiga", 0x0B6659, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:swamp", 0x07F9B2, Color.ORANGE, Luminosity.DARK, SaturationType.MEDIUM);
        add("minecraft:river", 0x0000FF, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:nether_wastes", 0xBF3B3B, Color.RED, Luminosity.LIGHT, SaturationType.MEDIUM);
        add("minecraft:the_end", 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:frozen_ocean", 0x7070D6, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:frozen_river", 0xA0A0FF, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:snowy_plains", 0xFFFFFF, Color.MONOCHROME, Luminosity.LIGHT, null);
        add("minecraft:mushroom_fields", 0xFF00FF, Color.PURPLE, Luminosity.BRIGHT, null);
        add("minecraft:beach", 0xFADE55, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:jungle", 0x537B09, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add("minecraft:sparse_jungle", 0x628B17, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add("minecraft:deep_ocean", 0x000030, Color.BLUE, Luminosity.DARK, null);
        add("minecraft:stony_shore", 0xA2A284, Color.GREEN, Luminosity.DARK, null);
        add("minecraft:snowy_beach", 0xFAF0C0, Color.YELLOW, Luminosity.LIGHT, null);
        add("minecraft:birch_forest", 0x307444, Color.GREEN, Luminosity.LIGHT, null);
        add("minecraft:dark_forest", 0x40511A, Color.GREEN, Luminosity.DARK, null);
        add("minecraft:snowy_taiga", 0x31554A, Color.BLUE, Luminosity.LIGHT, null);
        add("minecraft:old_growth_pine_taiga", 0x596651, Color.ORANGE, Luminosity.LIGHT, null);
        add("minecraft:windswept_forest", 0x507050, Color.MONOCHROME, Luminosity.BRIGHT, null);
        add("minecraft:savanna", 0xBDB25F, Color.GREEN, Luminosity.LIGHT, null);
        add("minecraft:savanna_plateau", 0xA79D64, Color.GREEN, Luminosity.LIGHT, null);
        add("minecraft:badlands", 0xD94515, Color.ORANGE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:wooded_badlands", 0xB09765, Color.ORANGE, Luminosity.BRIGHT, SaturationType.HIGH);
        add("minecraft:small_end_islands", 0xff1a8c, Color.PURPLE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:end_midlands", 0x8080FF, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:end_highlands", 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:end_barrens", 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.MEDIUM);
        add("minecraft:warm_ocean", 0x0000AC, Color.BLUE, Luminosity.BRIGHT, SaturationType.LOW);
        add("minecraft:lukewarm_ocean", 0x000090, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:cold_ocean", 0x202070, Color.BLUE, Luminosity.BRIGHT, SaturationType.HIGH);
        add("minecraft:deep_lukewarm_ocean", 0x000040, Color.BLUE, Luminosity.DARK, SaturationType.MEDIUM);
        add("minecraft:deep_cold_ocean", 0x202038, Color.BLUE, Luminosity.DARK, SaturationType.HIGH);
        add("minecraft:deep_frozen_ocean", 0x404090, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:the_void", 0x000000, Color.MONOCHROME, Luminosity.DARK, null);
        add("minecraft:sunflower_plains", 0xB5DB88, Color.GREEN, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:windswept_gravelly_hills", 0x789878, Color.MONOCHROME, Luminosity.LIGHT, null);
        add("minecraft:flower_forest", 0x2D8E49, Color.RED, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:ice_spikes", 0xB4DCDC, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add("minecraft:old_growth_birch_forest", 0x589C6C, Color.GREEN, Luminosity.LIGHT, null);
        add("minecraft:old_growth_spruce_taiga", 0x818E79, Color.ORANGE, Luminosity.DARK, SaturationType.HIGH);
        add("minecraft:windswept_savanna", 0xE5DA87, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add("minecraft:eroded_badlands", 0xFF6D3D, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add("minecraft:bamboo_jungle", 0x768E14, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add("minecraft:soul_sand_valley", 0x5E3830, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:crimson_forest", 0xDD0808, Color.RED, Luminosity.DARK, SaturationType.HIGH);
        add("minecraft:warped_forest", 0x49907B, Color.BLUE, Luminosity.BRIGHT, null);
        add("minecraft:basalt_deltas", 0x403636, Color.MONOCHROME, Luminosity.DARK, null);
        add("minecraft:dripstone_caves", 0xcc6600, Color.ORANGE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:lush_caves", 0x003300, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:meadow", 0xff00ff, Color.BLUE, Luminosity.BRIGHT, SaturationType.LOW);
        add("minecraft:grove", 0x80ff80, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:snowy_slopes", 0x00ffff, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:frozen_peaks", 0xA0A0A0, Color.MONOCHROME, Luminosity.LIGHT, null);
        add("minecraft:jagged_peaks", 0x3d7bc2, Color.MONOCHROME, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add("minecraft:stony_peaks", 0x888888, Color.MONOCHROME, Luminosity.LIGHT, null);
    }

    private static void add(String key, int color, Color randomColor, Luminosity luminosity, SaturationType saturation) {
        BIOME_HEX.put(key, color);
        BIOME_COLOR.put(key, randomColor);
        if (luminosity != null) {
            BIOME_LUMINOSITY.put(key, luminosity);
        }
        if (saturation != null) {
            BIOME_SATURATION.put(key, saturation);
        }
    }

    private static String norm(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim().toLowerCase();
        return k.indexOf(':') >= 0 ? k : "minecraft:" + k;
    }

    public static Integer getColor(String key) {
        return BIOME_HEX.get(norm(key));
    }

    public static Color getColorType(String key) {
        return BIOME_COLOR.get(norm(key));
    }

    public static Luminosity getColorLuminosity(String key) {
        return BIOME_LUMINOSITY.get(norm(key));
    }

    public static SaturationType getColorSaturation(String key) {
        return BIOME_SATURATION.get(norm(key));
    }
}
