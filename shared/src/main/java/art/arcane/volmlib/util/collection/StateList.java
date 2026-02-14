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

package art.arcane.volmlib.util.collection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StateList {
    private final List<String> states;

    public StateList(String... states) {
        this.states = new ArrayList<>(Arrays.asList(states));

        if (getBits() > 64) {
            throw new RuntimeException("StateLists cannot exceed 64 bits! You are trying to use " + getBits() + " bits!");
        }
    }

    public StateList(Enum<?>... states) {
        this.states = new ArrayList<>();
        for (Enum<?> state : states) {
            this.states.add(state.name());
        }

        if (getBits() > 64) {
            throw new RuntimeException("StateLists cannot exceed 64 bits! You are trying to use " + getBits() + " bits!");
        }
    }

    public long max() {
        return (long) (Math.pow(2, getBits()) - 1);
    }

    public List<String> getEnabled(long list) {
        List<String> f = new ArrayList<>();

        for (String i : states) {
            if (is(list, i)) {
                f.add(i);
            }
        }

        return f;
    }

    public long of(String... enabledStates) {
        long b = 0;

        for (String i : enabledStates) {
            b |= getBit(i);
        }

        return b;
    }

    public long set(long list, String state, boolean enabled) {
        long bit = getBit(state);
        boolean is = is(list, state);

        if (enabled && !is) {
            return list | bit;
        } else if (!enabled && is) {
            return list ^ bit;
        }

        return list;
    }

    public boolean is(long list, String state) {
        long bit = getBit(state);

        return bit > 0 && (list & bit) == bit;
    }

    public boolean hasBit(String state) {
        return getBit(state) > 0;
    }

    public long getBit(String state) {
        return getBit(states.indexOf(state));
    }

    public long getBit(int index) {
        return (long) (index < 0 ? -1 : Math.pow(2, index));
    }

    public int getBytes() {
        return getBits() == 0 ? 0 : ((getBits() >> 2) + 1);
    }

    public int getBits() {
        return states.size();
    }
}
