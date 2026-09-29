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

package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public abstract class TectonicPlateSupport<C> {
    private static final ThreadLocal<Boolean> ERRORS = ThreadLocal.withInitial(() -> false);

    public static final int MISSING = -1;
    public static final int CURRENT = 1;

    private final int sectionHeight;
    private final AtomicReferenceArray<C> chunks;
    private final AtomicBoolean closed;
    private final AtomicInteger pins;
    private final int x;
    private final int z;

    protected TectonicPlateSupport(int worldHeight, int x, int z) {
        this.sectionHeight = worldHeight >> 4;
        this.chunks = new AtomicReferenceArray<>(1024);
        this.closed = new AtomicBoolean(false);
        this.pins = new AtomicInteger();
        this.x = x;
        this.z = z;
    }

    protected TectonicPlateSupport(int worldHeight, CountingDataInputStream din, boolean versioned) throws IOException {
        this(worldHeight, din.readInt(), din.readInt());
        if (!din.markSupported()) {
            throw new IOException("Mark not supported!");
        }

        int version = versioned ? Varint.readUnsignedVarInt(din) : MISSING;
        for (int i = 0; i < chunks.length(); i++) {
            long size = din.readInt();
            if (size == 0) {
                continue;
            }

            long start = din.count();
            long end = start + size;

            beforeReadChunk(i);
            try {
                chunks.set(i, readChunk(version, sectionHeight, din));
                afterReadChunk(i);
                if (din.count() != end) {
                    // A reader that under- or over-consumed its payload poisons every later
                    // chunk in the plate; contain the damage to this one chunk.
                    throw new IOException("Mantle chunk " + i + " consumed " + (din.count() - start)
                            + " of " + size + " bytes");
                }
            } catch (Throwable e) {
                onReadChunkFailure(i, start, end, din, e);
                din.skipTo(end);
                addError();
            }
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    protected int getSectionHeight() {
        return sectionHeight;
    }

    public boolean inUse() {
        if (pins.get() > 0) {
            return true;
        }
        for (int i = 0; i < chunks.length(); i++) {
            C chunk = chunks.get(i);
            if (chunk != null && isChunkInUse(chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the plate open until {@link #unpin()}: while any pin is held no save seals the plate and a close
     * waits for it. Fails, taking no pin, once the plate is closed or being closed.
     */
    public boolean pin() {
        pins.incrementAndGet();
        if (closed.get()) {
            pins.decrementAndGet();
            return false;
        }
        return true;
    }

    public void unpin() {
        pins.decrementAndGet();
    }

    /**
     * Closes the plate for good, waiting for pins and then for chunk users to finish.
     */
    public void close() throws InterruptedException {
        closed.set(true);
        while (pins.get() > 0) {
            Thread.yield();
        }
        for (int i = 0; i < chunks.length(); i++) {
            C chunk = chunks.get(i);
            if (chunk != null) {
                closeChunk(chunk);
            }
        }
    }

    /**
     * Seals the plate for a save without waiting. Succeeds only when no pin and no chunk use is held, and leaves
     * the plate and every chunk open otherwise.
     */
    public boolean trySeal() {
        if (inUse()) {
            return false;
        }
        // A pin taken after this write sees the plate closed; one taken before it shows up in the check below.
        closed.set(true);
        if (pins.get() > 0) {
            closed.set(false);
            return false;
        }
        int length = chunks.length();
        for (int i = 0; i < length; i++) {
            C chunk = chunks.get(i);
            if (chunk != null && !tryLockChunk(chunk)) {
                unlockChunks(i, false);
                closed.set(false);
                return false;
            }
        }
        unlockChunks(length, true);
        return true;
    }

    private void unlockChunks(int end, boolean seal) {
        for (int i = 0; i < end; i++) {
            C chunk = chunks.get(i);
            if (chunk != null) {
                unlockChunk(chunk, seal);
            }
        }
    }

    public void reopen() {
        if (!closed.get()) {
            return;
        }
        for (int i = 0; i < chunks.length(); i++) {
            C chunk = chunks.get(i);
            if (chunk != null) {
                reopenChunk(chunk);
            }
        }
        closed.set(false);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean exists(int x, int z) {
        return get(x, z) != null;
    }

    public C get(int x, int z) {
        return chunks.get(index(x, z));
    }

    public void clear() {
        requireOpen();
        for (int i = 0; i < chunks.length(); i++) {
            chunks.set(i, null);
        }
    }

    public void delete(int x, int z) {
        requireOpen();
        deletePinned(x, z);
    }

    void deletePinned(int x, int z) {
        chunks.set(index(x, z), null);
    }

    public C getOrCreate(int x, int z) {
        requireOpen();
        return getOrCreatePinned(x, z);
    }

    /**
     * {@link #getOrCreate} for a caller holding a pin, which keeps the plate open even while a save or close is
     * deciding whether it may proceed.
     */
    C getOrCreatePinned(int x, int z) {
        final int index = index(x, z);
        final C chunk = chunks.get(index);
        if (chunk != null) {
            return chunk;
        }

        final C instance = createChunk(sectionHeight, x & 31, z & 31);
        final C value = chunks.compareAndExchange(index, null, instance);
        return value == null ? instance : value;
    }

    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(x);
        dos.writeInt(z);
        Varint.writeUnsignedVarInt(CURRENT, dos);

        var bytes = new ByteArrayOutputStream(8192);
        var sub = new DataOutputStream(bytes);
        for (int i = 0; i < chunks.length(); i++) {
            C chunk = chunks.get(i);
            if (chunk != null) {
                try {
                    writeChunk(chunk, sub);
                    dos.writeInt(bytes.size());
                    bytes.writeTo(dos);
                } finally {
                    bytes.reset();
                }
            } else {
                dos.writeInt(0);
            }
        }
    }

    protected int index(int x, int z) {
        return CacheKey.to1D(x, z, 0, 32, 32);
    }

    protected void beforeReadChunk(int index) {
    }

    protected void afterReadChunk(int index) {
    }

    protected void onReadChunkFailure(int index, long start, long end, CountingDataInputStream din, Throwable error) {
    }

    protected abstract C readChunk(int version, int sectionHeight, CountingDataInputStream din) throws IOException;

    protected abstract C createChunk(int sectionHeight, int x, int z);

    protected abstract boolean isChunkInUse(C chunk);

    protected abstract void closeChunk(C chunk) throws InterruptedException;

    protected abstract boolean tryLockChunk(C chunk);

    protected abstract void unlockChunk(C chunk, boolean seal);

    protected abstract void reopenChunk(C chunk);

    protected abstract void writeChunk(C chunk, DataOutputStream dos) throws IOException;

    public static void addError() {
        ERRORS.set(true);
    }

    public static boolean hasError() {
        try {
            return ERRORS.get();
        } finally {
            ERRORS.remove();
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new MantleClosedException("Tectonic Plate is closed!");
        }
    }
}
