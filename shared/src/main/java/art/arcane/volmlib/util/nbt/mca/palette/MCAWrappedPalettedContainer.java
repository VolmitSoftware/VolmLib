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

package art.arcane.volmlib.util.nbt.mca.palette;

import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.util.function.Function;

public class MCAWrappedPalettedContainer<T> implements MCAPaletteAccess {
    private final MCAPalettedContainer<T> container;
    private final Function<T, CompoundTag> reader;
    private final Function<CompoundTag, T> writer;

    public MCAWrappedPalettedContainer(MCAPalettedContainer<T> container, Function<T, CompoundTag> reader, Function<CompoundTag, T> writer) {
        this.container = container;
        this.reader = reader;
        this.writer = writer;
    }

    public void setBlock(int x, int y, int z, CompoundTag data) {
        container.set(x, y, z, writer.apply(data));
    }

    public CompoundTag getBlock(int x, int y, int z) {
        return reader.apply(container.get(x, y, z));
    }

    public void writeToSection(CompoundTag tag) {
        container.write(tag, "Palette", "BlockStates");
    }

    public void readFromSection(CompoundTag tag) {
        container.read(tag.getListTag("Palette"), tag.getLongArrayTag("BlockStates").getValue());
    }
}
