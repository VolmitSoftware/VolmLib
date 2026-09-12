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

package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import java.util.List;

public interface Rarity {
    static <T extends Rarity> ProceduralStream<T> stream(ProceduralStream<Double> noise, List<T> possibilities) {
        return ProceduralStream.of((x, z) -> pick(possibilities, noise.get(x, z)),
                (x, y, z) -> pick(possibilities, noise.get(x, y, z)),
                new Interpolated<T>() {
                    @Override
                    public double toDouble(T t) {
                        return 0;
                    }

                    @Override
                    public T fromDouble(double d) {
                        return null;
                    }
                });
    }


    static <T extends Rarity> T pickSlowly(List<T> possibilities, double noiseValue) {
        if (possibilities.isEmpty()) {
            return null;
        }

        if (possibilities.size() == 1) {
            return possibilities.get(0);
        }

        KList<T> rarityTypes = new KList<>();
        int totalRarity = 0;
        for (T i : possibilities) {
            totalRarity += Rarity.get(i);
        }

        for (T i : possibilities) {
            rarityTypes.addMultiple(i, totalRarity / Rarity.get(i));
        }

        return rarityTypes.get((int) (noiseValue * rarityTypes.last()));
    }

    static <T extends Rarity> T pick(List<T> possibilities, double noiseValue) {
        if (possibilities.isEmpty()) {
            return null;
        }

        if (possibilities.size() == 1) {
            return possibilities.get(0);
        }

        double total = 0;
        for (T i : possibilities) {
            total += 1d / Rarity.get(i);
        }

        double threshold = total * noiseValue;
        double buffer = 0;
        for (T i : possibilities) {
            buffer += 1d / Rarity.get(i);
            if (buffer >= threshold) {
                return i;
            }
        }

        return possibilities.get(possibilities.size() - 1);
    }

    static int get(Object v) {
        return v instanceof Rarity ? Math.max(1, ((Rarity) v).getRarity()) : 1;
    }

    /**
     * Expands a candidate list into a rarity-weighted list: each entry appears totalRarity/rarity
     * times, so picking uniformly from the result applies rarity exactly once. Both the Bukkit and
     * modded entity spawners select through this expansion; rarity must never be applied a second
     * time per spawn position.
     */
    static <T> KList<T> expandWeighted(List<T> possibilities) {
        KList<T> rarityTypes = new KList<>();
        int totalRarity = 0;
        for (T i : possibilities) {
            totalRarity += get(i);
        }
        for (T i : possibilities) {
            rarityTypes.addMultiple(i, totalRarity / get(i));
        }
        return rarityTypes;
    }

    int getRarity();
}
