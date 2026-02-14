package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.data.palette.Palette;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.function.Consumer4IO;
import art.arcane.volmlib.util.hunk.HunkLike;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.bits.Writable;
import art.arcane.volmlib.util.hunk.storage.PaletteOrHunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.util.BlockVector;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface MatterSlice<T> extends HunkLike<T>, Writable<T> {
    Class<T> getType();

    Palette<T> getGlobalPalette();

    void writeNode(T b, DataOutputStream dos) throws IOException;

    T readNode(DataInputStream din) throws IOException;

    <W> MatterWriter<W, T> writeInto(Class<W> mediumType);

    <W> MatterReader<W, T> readFrom(Class<W> mediumType);

    default void set(int x, int y, int z, T t) {
        if (x < 0 || y < 0 || z < 0 || x >= getWidth() || y >= getHeight() || z >= getDepth()) {
            return;
        }

        setRaw(x, y, z, t);
    }

    default T get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= getWidth() || y >= getHeight() || z >= getDepth()) {
            return null;
        }

        return getRaw(x, y, z);
    }

    default int getEntryCount() {
        if (this instanceof PaletteOrHunk<?> palette) {
            return palette.getEntryCount();
        }

        int count = 0;
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    if (getRaw(x, y, z) != null) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    default boolean isMapped() {
        return this instanceof PaletteOrHunk<?> palette && palette.isMapped();
    }

    default void applyFilter(MatterFilter<T> filter) {
        updateSync(filter::update);
    }

    default void updateSync(MatterFilter<T> filter) {
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    setRaw(x, y, z, filter.update(x, y, z, getRaw(x, y, z)));
                }
            }
        }
    }

    default void inject(MatterSlice<T> slice) {
        for (int x = 0; x < slice.getWidth(); x++) {
            for (int y = 0; y < slice.getHeight(); y++) {
                for (int z = 0; z < slice.getDepth(); z++) {
                    T value = slice.getRaw(x, y, z);
                    if (value != null) {
                        set(x, y, z, value);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    default void forceInject(MatterSlice<?> slice) {
        inject((MatterSlice<T>) slice);
    }

    @Override
    default T readNodeData(DataInputStream din) throws IOException {
        return readNode(din);
    }

    @Override
    default void writeNodeData(DataOutputStream dos, T t) throws IOException {
        writeNode(t, dos);
    }

    default Class<?> getClass(Object w) {
        Class<?> c = w.getClass();

        if (w instanceof World) {
            c = World.class;
        } else if (w instanceof BlockData) {
            c = BlockData.class;
        } else if (w instanceof Entity) {
            c = Entity.class;
        }

        return c;
    }

    default boolean writeInto(Location location) {
        return writeInto(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @SuppressWarnings("unchecked")
    default <W> boolean writeInto(W w, int x, int y, int z) {
        MatterWriter<W, T> injector = (MatterWriter<W, T>) writeInto((Class<W>) getClass(w));

        if (injector == null) {
            return false;
        }

        forEachValue((a, b, c, t) -> injector.writeMatter(w, t, a + x, b + y, c + z));
        return true;
    }

    default boolean readFrom(Location location) {
        return readFrom(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @SuppressWarnings("unchecked")
    default <W> boolean readFrom(W w, int x, int y, int z) {
        MatterReader<W, T> ejector = (MatterReader<W, T>) readFrom((Class<W>) getClass(w));

        if (ejector == null) {
            return false;
        }

        for (int i = x; i < x + getWidth(); i++) {
            for (int j = y; j < y + getHeight(); j++) {
                for (int k = z; k < z + getDepth(); k++) {
                    T value = ejector.readMatter(w, i, j, k);
                    if (value != null) {
                        set(i - x, j - y, k - z, value);
                    }
                }
            }
        }

        return true;
    }

    default boolean canWrite(Class<?> mediumType) {
        return writeInto((Class<Object>) mediumType) != null;
    }

    default boolean canRead(Class<?> mediumType) {
        return readFrom((Class<Object>) mediumType) != null;
    }

    default void write(DataOutputStream dos) throws IOException {
        dos.writeUTF(getType().getCanonicalName());

        if (this instanceof PaletteOrHunk<?> palette && palette.isPalette()) {
            palette.palette().writeDos(dos);
            return;
        }

        int w = getWidth();
        int h = getHeight();
        MatterPalette<T> palette = new MatterPalette<>(this);
        forEachValue((x, y, z, b) -> palette.assign(b));
        palette.writePalette(dos);
        dos.writeBoolean(isMapped());

        if (isMapped()) {
            Varint.writeUnsignedVarInt(getEntryCount(), dos);
            forEachValueIO((x, y, z, b) -> {
                Varint.writeUnsignedVarInt(CacheKey.to1D(x, y, z, w, h), dos);
                palette.writeNode(b, dos);
            });
        } else {
            forEachValueIO((x, y, z, b) -> palette.writeNode(b, dos));
        }
    }

    @SuppressWarnings("unchecked")
    default void read(DataInputStream din) throws IOException {
        if (this instanceof PaletteOrHunk<?> palette && palette.isPalette()) {
            ((PaletteOrHunk<T>) palette).setPalette(new DataContainer<>(din, this));
            return;
        }

        int w = getWidth();
        int h = getHeight();
        MatterPalette<T> palette = new MatterPalette<>(this, din);

        if (din.readBoolean()) {
            int nodes = Varint.readUnsignedVarInt(din);
            while (nodes-- > 0) {
                int[] pos = CacheKey.to3D(Varint.readUnsignedVarInt(din), w, h);
                setRaw(pos[0], pos[1], pos[2], palette.readNode(din));
            }
        } else {
            for (int x = 0; x < getWidth(); x++) {
                for (int y = 0; y < getHeight(); y++) {
                    for (int z = 0; z < getDepth(); z++) {
                        setRaw(x, y, z, palette.readNode(din));
                    }
                }
            }
        }
    }

    default boolean containsKey(BlockVector v) {
        return get(v.getBlockX(), v.getBlockY(), v.getBlockZ()) != null;
    }

    default void put(BlockVector v, T d) {
        set(v.getBlockX(), v.getBlockY(), v.getBlockZ(), d);
    }

    default T get(BlockVector v) {
        return get(v.getBlockX(), v.getBlockY(), v.getBlockZ());
    }

    private void forEachValue(Consumer4<Integer, Integer, Integer, T> consumer) {
        if (this instanceof PaletteOrHunk<?> palette) {
            ((PaletteOrHunk<T>) palette).iterateSync(consumer);
            return;
        }

        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    T value = getRaw(x, y, z);
                    if (value != null) {
                        consumer.accept(x, y, z, value);
                    }
                }
            }
        }
    }

    private void forEachValueIO(Consumer4IO<Integer, Integer, Integer, T> consumer) throws IOException {
        if (this instanceof PaletteOrHunk<?> palette) {
            ((PaletteOrHunk<T>) palette).iterateSyncIO(consumer);
            return;
        }

        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                for (int z = 0; z < getDepth(); z++) {
                    T value = getRaw(x, y, z);
                    if (value != null) {
                        consumer.accept(x, y, z, value);
                    }
                }
            }
        }
    }
}
