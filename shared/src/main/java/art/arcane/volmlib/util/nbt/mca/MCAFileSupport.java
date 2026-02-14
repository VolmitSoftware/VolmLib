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

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

@SuppressWarnings("ALL")
public class MCAFileSupport<C extends MCAChunkLike> {
    private final int regionX;
    private final int regionZ;
    private final Supplier<C> newChunkSupplier;
    private final Consumer<Runnable> deferredAfterSaveDispatcher;
    private final ConcurrentLinkedQueue<Runnable> afterSave;
    private AtomicReferenceArray<C> chunks;

    public MCAFileSupport(int regionX, int regionZ, Supplier<C> newChunkSupplier, Consumer<Runnable> deferredAfterSaveDispatcher) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.newChunkSupplier = newChunkSupplier;
        this.deferredAfterSaveDispatcher = deferredAfterSaveDispatcher;
        this.afterSave = new ConcurrentLinkedQueue<>();
    }

    public static int getChunkIndex(int chunkX, int chunkZ) {
        return (chunkX & 0x1F) + (chunkZ & 0x1F) * 32;
    }

    public void deserialize(RandomAccessFile raf, long loadFlags, IntFunction<C> chunkFactory) throws IOException {
        chunks = new AtomicReferenceArray<>(1024);
        for (int i = 0; i < 1024; i++) {
            raf.seek(i * 4L);
            int offset = raf.read() << 16;
            offset |= (raf.read() & 0xFF) << 8;
            offset |= raf.read() & 0xFF;
            if (raf.readByte() == 0) {
                continue;
            }

            raf.seek(4096L + i * 4L);
            int timestamp = raf.readInt();
            C chunk = chunkFactory.apply(timestamp);
            raf.seek(4096L * offset + 4L);
            chunk.deserialize(raf, loadFlags);
            chunks.set(i, chunk);
        }
    }

    public <P> KList<P> samplePositions(RandomAccessFile raf, BiFunction<Integer, Integer, P> pointFactory) throws IOException {
        KList<P> points = new KList<>();
        chunks = new AtomicReferenceArray<>(1024);
        int x = 0;
        int z = 0;

        for (int i = 0; i < 1024; i++) {
            x++;
            z++;

            raf.seek(i * 4L);
            int offset = raf.read() << 16;
            offset |= (raf.read() & 0xFF) << 8;
            offset |= raf.read() & 0xFF;
            if (raf.readByte() == 0) {
                continue;
            }

            points.add(pointFactory.apply(x & 31, (z / 31) & 31));
        }

        return points;
    }

    public AtomicReferenceArray<C> getChunks() {
        return chunks;
    }

    public int serialize(RandomAccessFile raf) throws IOException {
        return serialize(raf, false);
    }

    public int serialize(RandomAccessFile raf, boolean changeLastUpdate) throws IOException {
        int globalOffset = 2;
        int lastWritten = 0;
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        int chunksWritten = 0;
        int chunkXOffset = MCAUtilSupport.regionToChunk(regionX);
        int chunkZOffset = MCAUtilSupport.regionToChunk(regionZ);

        if (chunks == null) {
            return 0;
        }

        for (int cx = 0; cx < 32; cx++) {
            for (int cz = 0; cz < 32; cz++) {
                int index = getChunkIndex(cx, cz);
                C chunk = chunks.get(index);
                if (chunk == null) {
                    continue;
                }

                raf.seek(4096L * globalOffset);
                lastWritten = chunk.serialize(raf, chunkXOffset + cx, chunkZOffset + cz);
                if (lastWritten == 0) {
                    continue;
                }

                chunksWritten++;
                int sectors = (lastWritten >> 12) + (lastWritten % 4096 == 0 ? 0 : 1);

                raf.seek(index * 4L);
                raf.writeByte(globalOffset >>> 16);
                raf.writeByte(globalOffset >> 8 & 0xFF);
                raf.writeByte(globalOffset & 0xFF);
                raf.writeByte(sectors);

                raf.seek(index * 4L + 4096L);
                raf.writeInt(changeLastUpdate ? timestamp : chunk.getLastMCAUpdate());

                globalOffset += sectors;
            }
        }

        if (lastWritten % 4096 != 0) {
            raf.seek(globalOffset * 4096L - 1L);
            raf.write(0);
        }

        Runnable afterSaveWork = () -> afterSave.forEach(Runnable::run);
        if (deferredAfterSaveDispatcher != null) {
            deferredAfterSaveDispatcher.accept(afterSaveWork);
        } else {
            afterSaveWork.run();
        }

        return chunksWritten;
    }

    public void setChunk(int index, C chunk) {
        checkIndex(index);
        if (chunks == null) {
            chunks = new AtomicReferenceArray<>(1024);
        }
        chunks.set(index, chunk);
    }

    public void setChunk(int chunkX, int chunkZ, C chunk) {
        setChunk(getChunkIndex(chunkX, chunkZ), chunk);
    }

    public C getChunk(int index) {
        checkIndex(index);
        if (chunks == null) {
            return null;
        }
        return chunks.get(index);
    }

    public C getChunk(int chunkX, int chunkZ) {
        return getChunk(getChunkIndex(chunkX, chunkZ));
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return getChunk(chunkX, chunkZ) != null;
    }

    public void setBiomeAt(int blockX, int blockY, int blockZ, int biomeID) {
        createChunkIfMissing(blockX, blockZ).setBiomeAt(blockX, blockY, blockZ, biomeID);
    }

    public int getBiomeAt(int blockX, int blockY, int blockZ) {
        int chunkX = MCAUtilSupport.blockToChunk(blockX);
        int chunkZ = MCAUtilSupport.blockToChunk(blockZ);
        C chunk = getChunk(getChunkIndex(chunkX, chunkZ));
        if (chunk == null) {
            return -1;
        }
        return chunk.getBiomeAt(blockX, blockY, blockZ);
    }

    public void setBlockStateAt(int blockX, int blockY, int blockZ, CompoundTag state, boolean cleanup) {
        createChunkIfMissing(blockX, blockZ).setBlockStateAt(blockX, blockY, blockZ, state, cleanup);
    }

    public CompoundTag getBlockStateAt(int blockX, int blockY, int blockZ) {
        int chunkX = MCAUtilSupport.blockToChunk(blockX);
        int chunkZ = MCAUtilSupport.blockToChunk(blockZ);
        C chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        return chunk.getBlockStateAt(blockX, blockY, blockZ);
    }

    public void afterSave(Runnable callback) {
        afterSave.add(callback);
    }

    protected C createChunkIfMissing(int blockX, int blockZ) {
        int chunkX = MCAUtilSupport.blockToChunk(blockX);
        int chunkZ = MCAUtilSupport.blockToChunk(blockZ);
        C chunk = getChunk(chunkX, chunkZ);
        if (chunk == null) {
            chunk = newChunkSupplier.get();
            setChunk(getChunkIndex(chunkX, chunkZ), chunk);
        }
        return chunk;
    }

    protected int getRegionX() {
        return regionX;
    }

    protected int getRegionZ() {
        return regionZ;
    }

    private int checkIndex(int index) {
        if (index < 0 || index > 1023) {
            throw new IndexOutOfBoundsException();
        }
        return index;
    }
}
