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

package art.arcane.volmlib.util.hunk.bits;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.function.Consumer2;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class HashPalette<T> implements Palette<T> {
    private final Object lock = new Object();
    private final KMap<T, Integer> palette;
    private final AtomicInteger size;
    private volatile AtomicReferenceArray<T> lookup;

    public HashPalette() {
        this.size = new AtomicInteger(1);
        this.palette = new KMap<>();
        this.lookup = new AtomicReferenceArray<>(32);
    }

    @Override
    public T get(int id) {
        if (id <= 0 || id >= size.get()) {
            return null;
        }

        AtomicReferenceArray<T> values = lookup;
        return id < values.length() ? values.get(id) : null;
    }

    @Override
    public int add(T t) {
        if (t == null) {
            return 0;
        }

        return palette.computeIfAbsent(t, $ -> {
            synchronized (lock) {
                int index = size.getAndIncrement();
                store(index, t);
                return index;
            }
        });
    }

    @Override
    public int id(T t) {
        if (t == null) {
            return 0;
        }

        Integer v = palette.get(t);
        return v != null ? v : -1;
    }

    @Override
    public int size() {
        return size.get() - 1;
    }

    @Override
    public void iterate(Consumer2<T, Integer> c) {
        synchronized (lock) {
            AtomicReferenceArray<T> values = lookup;
            for (int i = 1; i < size.get(); i++) {
                c.accept(values.get(i), i);
            }
        }
    }

    @Override
    public Palette<T> from(Palette<T> oldPalette) {
        synchronized (lock) {
            oldPalette.iterate((t, i) -> {
                if (t == null) throw new NullPointerException("Null palette entries are not allowed!");
                store(i, t);
                palette.put(t, i);
            });
            size.set(oldPalette.size() + 1);
        }
        return this;
    }

    @Override
    public Palette<T> from(int size, Writable<T> writable, DataInputStream in) throws IOException {
        synchronized (lock) {
            for (int i = 1; i <= size; i++) {
                T t = writable.readNodeData(in);
                if (t == null) throw new NullPointerException("Null palette entries are not allowed!");
                store(i, t);
                palette.put(t, i);
            }
            this.size.set(size + 1);
        }
        return this;
    }

    private void store(int index, T value) {
        AtomicReferenceArray<T> values = lookup;
        if (index >= values.length()) {
            AtomicReferenceArray<T> grown = new AtomicReferenceArray<>(Math.max(index + 1, values.length() << 1));
            for (int i = 0; i < values.length(); i++) {
                grown.set(i, values.get(i));
            }
            grown.set(index, value);
            lookup = grown;
            return;
        }
        values.set(index, value);
    }
}
