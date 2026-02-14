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

package art.arcane.volmlib.util.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DataPalette<T> {
    private final List<T> palette;

    public DataPalette() {
        this(new ArrayList<>(16));
    }

    public DataPalette(List<T> palette) {
        this.palette = palette;
    }

    public static <T> DataPalette<T> getPalette(IOAdapter<T> adapter, DataInputStream din) throws IOException {
        List<T> palette = new ArrayList<>();
        int s = din.readShort() - Short.MIN_VALUE;

        for (int i = 0; i < s; i++) {
            palette.add(adapter.read(din));
        }

        return new DataPalette<>(palette);
    }

    public List<T> getPalette() {
        return palette;
    }

    public T get(int index) {
        synchronized (palette) {
            if (index < 0 || index >= palette.size()) {
                return null;
            }

            return palette.get(index);
        }
    }

    public int getIndex(T t) {
        int v = 0;

        synchronized (palette) {
            v = palette.indexOf(t);

            if (v == -1) {
                v = palette.size();
                palette.add(t);
            }
        }

        return v;
    }

    public void write(IOAdapter<T> adapter, DataOutputStream dos) throws IOException {
        synchronized (palette) {
            dos.writeShort(getPalette().size() + Short.MIN_VALUE);

            for (T t : palette) {
                adapter.write(t, dos);
            }
        }
    }
}
