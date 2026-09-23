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

package art.arcane.volmlib.util.matter.slices;

import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.hunk.storage.MappedHunk;
import art.arcane.volmlib.util.hunk.storage.PaletteOrHunk;
import art.arcane.volmlib.util.matter.MatterReader;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterWriter;
import lombok.Getter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public abstract class RawMatter<T> extends PaletteOrHunk<T> implements MatterSlice<T> {
    private volatile KMap<Class<?>, MatterWriter<?, T>> writers;
    private volatile KMap<Class<?>, MatterReader<?, T>> readers;
    @Getter
    private final Class<T> type;

    public RawMatter(int width, int height, int depth, Class<T> type) {
        super(width, height, depth, true, () -> new MappedHunk<>(width, height, depth));
        this.type = type;
    }

    @Override
    public T get(int x, int y, int z) {
        return MatterSlice.super.get(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, T value) {
        MatterSlice.super.set(x, y, z, value);
    }

    protected synchronized <W> void registerWriter(Class<W> mediumType, MatterWriter<W, T> injector) {
        if (writers == null) {
            writers = new KMap<>();
        }
        writers.put(mediumType, injector);
    }

    protected synchronized <W> void registerReader(Class<W> mediumType, MatterReader<W, T> injector) {
        if (readers == null) {
            readers = new KMap<>();
        }
        readers.put(mediumType, injector);
    }

    @Override
    public <W> MatterWriter<W, T> writeInto(Class<W> mediumType) {
        Objects.requireNonNull(mediumType, "mediumType");
        KMap<Class<?>, MatterWriter<?, T>> registered = writers;
        return registered == null ? null : (MatterWriter<W, T>) registered.get(mediumType);
    }

    @Override
    public <W> MatterReader<W, T> readFrom(Class<W> mediumType) {
        Objects.requireNonNull(mediumType, "mediumType");
        KMap<Class<?>, MatterReader<?, T>> registered = readers;
        return registered == null ? null : (MatterReader<W, T>) registered.get(mediumType);
    }

    @Override
    public abstract void writeNode(T b, DataOutputStream dos) throws IOException;

    @Override
    public abstract T readNode(DataInputStream din) throws IOException;
}
