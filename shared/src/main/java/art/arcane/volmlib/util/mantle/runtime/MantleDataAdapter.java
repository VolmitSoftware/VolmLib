package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.DataOutputStream;
import java.io.IOException;

public interface MantleDataAdapter<M> {
    M createSection();

    M readSection(CountingDataInputStream din) throws IOException;

    void writeSection(M section, DataOutputStream dos) throws IOException;

    void trimSection(M section);

    boolean isSectionEmpty(M section);

    Class<?> classifyValue(Object value);

    <T> void set(M section, int x, int y, int z, Class<?> type, T value);

    <T> void remove(M section, int x, int y, int z, Class<T> type);

    <T> T get(M section, int x, int y, int z, Class<T> type);

    <T> void iterate(M section, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator);

    boolean hasSlice(M section, Class<?> type);

    void deleteSlice(M section, Class<?> type);
}
