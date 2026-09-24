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

import art.arcane.volmlib.util.data.Varint;
import org.apache.commons.lang3.Validate;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataContainer<T> {
    private static final boolean TRIM = Boolean.getBoolean("iris.trim-palette");
    private static final int PRESENT = -1;
    private static final long[] ALIGNED_FIRST_WORD_MASK = alignedFirstWordMasks();
    protected static final int INITIAL_BITS = 3;
    protected static final int LINEAR_BITS_LIMIT = 4;
    protected static final int LINEAR_INITIAL_LENGTH = (int) Math.pow(2, LINEAR_BITS_LIMIT) + 2;
    protected static final int[] BIT = computeBitLimits();
    private final Lock read, write;

    private volatile Palette<T> palette;
    private volatile DataBits data;
    private final int length;
    private final Writable<T> writer;

    public DataContainer(Writable<T> writer, int length) {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.read = lock.readLock();
        this.write = lock.writeLock();

        this.writer = writer;
        this.length = length;
        this.data = new DataBits(length == 4096 ? memoryBits(1) : INITIAL_BITS, length == 4096 ? 64 : length);
        this.palette = newPalette(INITIAL_BITS);
    }

    public DataContainer(DataInputStream din, Writable<T> writer) throws IOException {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.read = lock.readLock();
        this.write = lock.writeLock();

        this.writer = writer;
        this.length = Varint.readUnsignedVarInt(din);
        this.palette = newPalette(din);
        this.data = readData(din);

        // writeDos() always trims before serializing, so anything we read back is already minimal.
        // Re-scanning every entry on load is pure overhead; -Diris.trim-palette=true restores it.
        if (TRIM) {
            trim();
        }
    }

    private static long[] alignedFirstWordMasks() {
        long[] masks = new long[16];
        for (int bits = 1; bits < masks.length; bits++) {
            long valueMask = (1L << bits) - 1L;
            for (int position = 0; position < 64 / bits; position++) {
                if ((position & 0x333) == 0) {
                    masks[bits] |= valueMask << (position * bits);
                }
            }
        }
        return masks;
    }

    private DataBits readData(DataInputStream input) throws IOException {
        int wireBits = palette.bits();
        if (length != 4096) {
            return new DataBits(wireBits, length, input);
        }
        long firstWord = Varint.readUnsignedVarLong(input);
        if ((firstWord & ~ALIGNED_FIRST_WORD_MASK[wireBits]) != 0L) {
            return readFullWords(new DataBits(wireBits, length), input, firstWord, 0);
        }
        return readCompact(input, wireBits, firstWord);
    }

    private static DataBits readFullWords(DataBits full, DataInputStream input, long firstWord, int word) throws IOException {
        full.getRaw().setPlain(word++, firstWord);
        while (word < full.getRaw().length()) {
            full.getRaw().setPlain(word++, Varint.readUnsignedVarLong(input));
        }
        return full;
    }

    private DataBits readCompact(DataInputStream input, int wireBits, long packed) throws IOException {
        int bits = memoryBits(palette.size() + 1);
        DataBits loaded = new DataBits(bits, 64);
        int valuesPerLong = 64 / wireBits;
        long wireMask = (1L << wireBits) - 1L;
        int memoryMask = (1 << bits) - 1;
        for (int position = 0; position < length;) {
            int count = Math.min(valuesPerLong, length - position);
            if (packed != 0L) {
                for (int offset = 0; offset < count; offset++) {
                    int id = (int) ((packed >>> (offset * wireBits)) & wireMask);
                    if (id == 0) {
                        continue;
                    }
                    if (id > memoryMask) {
                        Validate.inclusiveBetween(0L, memoryMask, id);
                    }
                    int index = position + offset;
                    if ((index & 0x333) != 0) {
                        DataBits expanded = expandedData(loaded, wireBits, position);
                        return readFullWords(expanded, input, packed, position / valuesPerLong);
                    }
                    setUnpublished(loaded, compactPosition(index), id);
                }
            }
            position += count;
            if (position < length) {
                packed = Varint.readUnsignedVarLong(input);
            }
        }
        return loaded;
    }

    private static void setUnpublished(DataBits bits, int position, int id) {
        int valuesPerLong = 64 / bits.getBits();
        int word = position / valuesPerLong;
        int shift = (position % valuesPerLong) * bits.getBits();
        bits.getRaw().setPlain(word, bits.getRaw().getPlain(word) | ((long) id << shift));
    }

    private static int compactPosition(int position) {
        return ((position & 0xc) >>> 2) | ((position & 0xc0) >>> 4) | ((position & 0xc00) >>> 6);
    }

    private static int expandedPosition(int position) {
        return ((position & 3) << 2) | ((position & 12) << 4) | ((position & 48) << 6);
    }

    private int logicalId(DataBits bits, int position) {
        if (bits.getSize() == length) {
            return bits.getUnchecked(position);
        }
        return (position & 0x333) == 0 ? bits.getUnchecked(compactPosition(position)) : 0;
    }

    private DataBits expandedData(DataBits compact, int bits, int limit) {
        DataBits expanded = new DataBits(bits, length);
        for (int position = 0; position < compact.getSize() && expandedPosition(position) < limit; position++) {
            setUnpublished(expanded, expandedPosition(position), compact.getUnchecked(position));
        }
        return expanded;
    }

    private static int[] computeBitLimits() {
        int[] m = new int[16];

        for (int i = 0; i < m.length; i++) {
            m[i] = (int) Math.pow(2, i);
        }

        return m;
    }

    protected static int bits(int size) {
        if (DataContainer.BIT[INITIAL_BITS] >= size) {
            return INITIAL_BITS;
        }

        for (int i = 0; i < DataContainer.BIT.length; i++) {
            if (DataContainer.BIT[i] >= size) {
                return i;
            }
        }

        return DataContainer.BIT.length - 1;
    }

    private static int memoryBits(int size) {
        if (size <= 2) {
            return 1;
        }
        if (size <= 4) {
            return 2;
        }
        return bits(size);
    }

    public String toString() {
        return "DataContainer <" + length + " x " + data.getBits() + " bits> -> Palette<" + palette.getClass().getSimpleName().replaceAll("\\QPalette\\E", "") + ">: " + palette.size() +
                " " + data.toString() + " PalBit: " + palette.bits();
    }

    public byte[] write() throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(boas);
        return boas.toByteArray();
    }

    public void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    public void writeDos(DataOutputStream dos) throws IOException {
        write.lock();
        try {
            trim();
            Varint.writeUnsignedVarInt(length, dos);
            Varint.writeUnsignedVarInt(palette.size(), dos);
            palette.iterateIO((data, __) -> writer.writeNodeData(dos, data));
            writeData(dos);
            dos.flush();
        } finally {
            write.unlock();
        }
    }

    private void writeData(DataOutputStream dos) throws IOException {
        int wireBits = palette.bits();
        if (data.getSize() == length && data.getBits() == wireBits) {
            data.write(dos);
            return;
        }
        int valuesPerLong = 64 / wireBits;
        if (data.getSize() != length) {
            int word = 0;
            long packed = 0L;
            for (int position = 0; position < data.getSize(); position++) {
                int id = data.getUnchecked(position);
                if (id == 0) {
                    continue;
                }
                int index = expandedPosition(position);
                int targetWord = index / valuesPerLong;
                while (word < targetWord) {
                    Varint.writeUnsignedVarLong(packed, dos);
                    packed = 0L;
                    word++;
                }
                packed |= (long) id << ((index % valuesPerLong) * wireBits);
            }
            int wordCount = (length + valuesPerLong - 1) / valuesPerLong;
            while (word < wordCount) {
                Varint.writeUnsignedVarLong(packed, dos);
                packed = 0L;
                word++;
            }
            return;
        }
        int position = 0;
        while (position < length) {
            long packed = 0L;
            for (int valueIndex = 0; valueIndex < valuesPerLong && position < length; valueIndex++) {
                packed |= (long) logicalId(data, position++) << (valueIndex * wireBits);
            }
            Varint.writeUnsignedVarLong(packed, dos);
        }
    }

    private Palette<T> newPalette(DataInputStream din) throws IOException {
        int paletteSize = Varint.readUnsignedVarInt(din);
        Palette<T> d = newPalette(bits(paletteSize + 1));
        d.from(paletteSize, writer, din);
        return d;
    }

    private Palette<T> newPalette(int bits) {
        if (bits <= LINEAR_BITS_LIMIT) {
            return new LinearPalette<>(LINEAR_INITIAL_LENGTH);
        }

        return new HashPalette<>();
    }

    public void set(int position, T t) {
        Validate.inclusiveBetween(0L, (length - 1L), position);
        read.lock();
        try {
            boolean compact = data.getSize() != length;
            if (compact && (position & 0x333) != 0) {
                if (t == null) {
                    return;
                }
            } else {
                int id = palette.id(t);
                if (id >= 0) {
                    data.set(compact ? compactPosition(position) : position, id);
                    return;
                }
            }
        } finally {
            read.unlock();
        }

        write.lock();
        try {
            if (data.getSize() != length && (position & 0x333) != 0) {
                data = expandedData(data, palette.bits(), length);
            }
            int id = palette.id(t);
            if (id < 0) {
                id = palette.add(t);
            }
            updateBits();
            data.set(data.getSize() != length ? compactPosition(position) : position, id);
        } finally {
            write.unlock();
        }
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void updateBits() {
        int bits = data.getSize() != length ? memoryBits(palette.size() + 1) : palette.bits();
        if (bits == data.getBits())
            return;

        if (data.getBits() <= LINEAR_BITS_LIMIT != bits <= LINEAR_BITS_LIMIT) {
            palette = newPalette(bits).from(palette);
        }

        data = data.setBits(bits);
    }

    public void copyTo(int[] positions, T[] destination) {
        if (destination.length != positions.length) {
            throw new IllegalArgumentException("Position and destination lengths must match");
        }
        for (int position : positions) {
            Validate.inclusiveBetween(0L, length - 1L, position);
        }
        read.lock();
        try {
            DataBits localData = data;
            for (int index = 0; index < positions.length; index++) {
                int id = logicalId(localData, positions[index]);
                destination[index] = id <= 0 ? null : palette.get(id);
            }
        } finally {
            read.unlock();
        }
    }

    public T get(int position) {
        Validate.inclusiveBetween(0L, (length - 1L), position);
        read.lock();
        try {
            DataBits localData = data;
            int id = logicalId(localData, position);
            return id <= 0 ? null : palette.get(id);
        } finally {
            read.unlock();
        }
    }

    /**
     * True when no position holds a value. Short-circuits on the first present entry, so it
     * costs at most one full bit-scan — the same class of work writeDos' trim already does.
     */
    public boolean isEmptyData() {
        read.lock();
        try {
            DataBits bits = data;
            for (int position = 0; position < bits.getSize(); position++) {
                if (bits.getUnchecked(position) > 0) {
                    return false;
                }
            }
            return true;
        } finally {
            read.unlock();
        }
    }

    public void iteratePresent(IndexedConsumer<T> consumer) {
        read.lock();
        try {
            DataBits localData = data;
            boolean compact = localData.getSize() != length;
            for (int position = 0; position < localData.getSize(); position++) {
                int id = localData.getUnchecked(position);
                if (id > 0) {
                    consumer.accept(compact ? expandedPosition(position) : position, palette.get(id));
                }
            }
        } finally {
            read.unlock();
        }
    }

    public void iteratePresentIO(IndexedIOConsumer<T> consumer) throws IOException {
        read.lock();
        try {
            DataBits localData = data;
            boolean compact = localData.getSize() != length;
            for (int position = 0; position < localData.getSize(); position++) {
                int id = localData.getUnchecked(position);
                if (id > 0) {
                    consumer.accept(compact ? expandedPosition(position) : position, palette.get(id));
                }
            }
        } finally {
            read.unlock();
        }
    }

    public int size() {
        return length;
    }

    /**
     * Renumbers the palette down to the ids actually referenced by the data.
     * <p>
     * Byte-identical to the previous Int2IntRBTreeMap implementation: old ids are visited in
     * ascending order (the tree map iterated its keys sorted), so {@link Palette#add(Object)}
     * hands out exactly the same new ids, and absent/zero ids still map to 0.
     */
    private void trim() {
        DataBits localData = data;
        int[] remap = new int[Math.max(palette.size() + 1, 16)];
        int distinct = 0;
        int maxId = 0;

        for (int i = 0; i < localData.getSize(); i++) {
            int x = localData.getUnchecked(i);
            if (x <= 0) continue;
            if (x >= remap.length) {
                remap = Arrays.copyOf(remap, Math.max(x + 1, remap.length << 1));
            }
            if (remap[x] == 0) {
                remap[x] = PRESENT;
                distinct++;
                if (x > maxId) {
                    maxId = x;
                }
            }
        }

        if (distinct == palette.size())
            return;

        int bits = localData.getSize() != length ? memoryBits(distinct + 1) : bits(distinct + 1);
        Palette<T> trimmed = newPalette(bits);
        for (int id = 1; id <= maxId; id++) {
            if (remap[id] != 0) {
                remap[id] = trimmed.add(palette.get(id));
            }
        }

        DataBits tBits = new DataBits(bits, localData.getSize());
        for (int i = 0; i < localData.getSize(); i++) {
            int x = localData.getUnchecked(i);
            setUnpublished(tBits, i, x <= 0 ? 0 : remap[x]);
        }

        data = tBits;
        palette = trimmed;
    }

    @FunctionalInterface
    public interface IndexedConsumer<T> {
        void accept(int position, T value);
    }

    @FunctionalInterface
    public interface IndexedIOConsumer<T> {
        void accept(int position, T value) throws IOException;
    }
}
