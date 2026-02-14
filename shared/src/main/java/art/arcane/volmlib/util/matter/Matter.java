package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.math.BlockPosition;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface Matter {
    int VERSION = 1;

    static Matter read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    static Matter read(InputStream in) throws IOException {
        return read(in, size -> new IrisMatter(size.getX(), size.getY(), size.getZ()));
    }

    static Matter readDin(CountingDataInputStream din) throws IOException {
        return readDin(din, size -> new IrisMatter(size.getX(), size.getY(), size.getZ()));
    }

    static Matter read(InputStream in, Function<BlockPosition, Matter> matterFactory) throws IOException {
        return readDin(CountingDataInputStream.wrap(in), matterFactory);
    }

    static Matter readDin(CountingDataInputStream din, Function<BlockPosition, Matter> matterFactory) throws IOException {
        Matter matter = matterFactory.apply(new BlockPosition(din.readInt(), din.readInt(), din.readInt()));
        int sliceCount = Byte.toUnsignedInt(din.readByte());
        matter.getHeader().read(din);

        for (int i = 0; i < sliceCount; i++) {
            int size = din.readInt();
            if (size <= 0) {
                continue;
            }

            long start = din.count();
            long end = start + size;

            try {
                String canonicalName = din.readUTF();
                Class<?> type = Class.forName(canonicalName);
                MatterSlice<?> slice = matter.createSlice(type, matter);

                if (slice != null) {
                    slice.read(din);
                    matter.putSlice(type, slice);
                }
            } catch (Throwable ignored) {
                // Unknown or incompatible slice payload; skip to the advertised boundary.
            }

            if (din.count() < end) {
                din.skipTo(end);
            }

            if (din.count() != end) {
                throw new IOException("Matter slice read size mismatch");
            }
        }

        return matter;
    }

    MatterHeader getHeader();

    int getWidth();

    int getHeight();

    int getDepth();

    <T> MatterSlice<T> createSlice(Class<T> type, Matter matter);

    Map<Class<?>, MatterSlice<?>> getSliceMap();

    default BlockPosition getCenter() {
        return new BlockPosition(getCenterX(), getCenterY(), getCenterZ());
    }

    default BlockPosition getSize() {
        return new BlockPosition(getWidth(), getHeight(), getDepth());
    }

    default int getCenterX() {
        return (int) Math.round(getWidth() / 2D);
    }

    default int getCenterY() {
        return (int) Math.round(getHeight() / 2D);
    }

    default int getCenterZ() {
        return (int) Math.round(getDepth() / 2D);
    }

    @SuppressWarnings("unchecked")
    default <T> MatterSlice<T> getSlice(Class<T> t) {
        return (MatterSlice<T>) getSliceMap().get(t);
    }

    @SuppressWarnings("unchecked")
    default <T> MatterSlice<T> deleteSlice(Class<?> c) {
        return (MatterSlice<T>) getSliceMap().remove(c);
    }

    @SuppressWarnings("unchecked")
    default <T> MatterSlice<T> putSlice(Class<?> c, MatterSlice<T> slice) {
        return (MatterSlice<T>) getSliceMap().put(c, slice);
    }

    @SuppressWarnings("unchecked")
    default <T> MatterSlice<T> slice(Class<?> c) {
        MatterSlice<T> slice = (MatterSlice<T>) getSlice(c);
        if (slice != null) {
            return slice;
        }

        slice = (MatterSlice<T>) createSlice((Class<T>) c, this);
        if (slice == null) {
            throw new IllegalArgumentException("Unsupported matter slice type " + c.getCanonicalName());
        }

        putSlice(c, slice);
        return slice;
    }

    default boolean hasSlice(Class<?> c) {
        return getSlice(c) != null;
    }

    default void clearSlices() {
        getSliceMap().clear();
    }

    default Set<Class<?>> getSliceTypes() {
        return getSliceMap().keySet();
    }

    default Matter copy() {
        Matter copy = new IrisMatter(getWidth(), getHeight(), getDepth());
        copy.getHeader().setAuthor(getHeader().getAuthor());
        copy.getHeader().setCreatedAt(getHeader().getCreatedAt());
        copy.getHeader().setVersion(getHeader().getVersion());

        for (Map.Entry<Class<?>, MatterSlice<?>> entry : getSliceMap().entrySet()) {
            MatterSlice<?> source = entry.getValue();
            if (source == null) {
                continue;
            }

            MatterSlice<Object> target = (MatterSlice<Object>) copy.slice(entry.getKey());
            target.forceInject(source);
        }

        return copy;
    }

    default void trimSlices() {
        Set<Class<?>> remove = new KSet<>();
        for (Class<?> type : new ArrayList<>(getSliceTypes())) {
            MatterSlice<?> slice = getSlice(type);
            if (slice != null && slice.getEntryCount() == 0) {
                remove.add(type);
            }
        }

        for (Class<?> type : remove) {
            deleteSlice(type);
        }
    }

    default void write(File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            write(out);
        }
    }

    default void write(OutputStream out) throws IOException {
        writeDos(new DataOutputStream(out));
    }

    default void writeDos(DataOutputStream dos) throws IOException {
        trimSlices();
        dos.writeInt(getWidth());
        dos.writeInt(getHeight());
        dos.writeInt(getDepth());
        dos.writeByte(getSliceTypes().size());
        getHeader().write(dos);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
        DataOutputStream sub = new DataOutputStream(bytes);

        for (Class<?> type : getSliceTypes()) {
            MatterSlice<?> slice = getSlice(type);
            if (slice == null) {
                continue;
            }

            try {
                slice.write(sub);
                dos.writeInt(bytes.size());
                bytes.writeTo(dos);
            } finally {
                bytes.reset();
            }
        }
    }

    default int getTotalCount() {
        int count = 0;
        for (MatterSlice<?> slice : getSliceMap().values()) {
            if (slice != null) {
                count += slice.getEntryCount();
            }
        }

        return count;
    }
}
