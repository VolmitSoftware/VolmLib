package art.arcane.volmlib.util.mantle.runtime;

import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.IOException;

public interface MantleHooks {
    MantleHooks NONE = new MantleHooks() {
    };

    default void onBeforeReadSection(int index) {
    }

    default void onReadSectionFailure(int index, long start, long end, CountingDataInputStream din, IOException error) {
    }

    default void onBeforeReadChunk(int index) {
    }

    default void onAfterReadChunk(int index) {
    }

    default void onReadChunkFailure(int index, long start, long end, CountingDataInputStream din, Throwable error) {
    }

    default boolean shouldRetainSlice(Class<?> sliceType) {
        return false;
    }

    default String formatDuration(double millis) {
        return millis + "ms";
    }

    default void onDebug(String message) {
    }

    default void onWarn(String message) {
        System.err.println(message);
    }

    default void onError(Throwable throwable) {
        throwable.printStackTrace();
    }
}
