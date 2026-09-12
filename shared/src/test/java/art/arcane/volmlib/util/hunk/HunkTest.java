package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.hunk.storage.ArrayHunk;
import art.arcane.volmlib.util.hunk.storage.AtomicHunk;
import art.arcane.volmlib.util.parallel.BurstExecutorSupport;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HunkTest {
    @Test
    public void croppedViewsWriteThroughAndCopiesRemainIndependent() {
        Hunk<String> source = new ArrayHunk<>(5, 6, 7);
        source.set(2, 3, 4, "source");
        Hunk<String> view = source.croppedView(1, 2, 3, 4, 5, 6);
        Hunk<String> copy = source.crop(1, 2, 3, 4, 5, 6);
        assertEquals("source", view.get(1, 1, 1));
        assertEquals("source", copy.get(1, 1, 1));
        view.set(1, 1, 1, "view");
        assertEquals("view", source.get(2, 3, 4));
        assertEquals("source", copy.get(1, 1, 1));
    }

    @Test
    public void convertedViewsReadAndWriteTheSharedStorage() {
        Hunk<Integer> source = Hunk.newArrayHunk(2, 3, 4);
        source.set(1, 2, 3, 17);
        Hunk<String> view = Hunk.convertedReadWriteView(source, String::valueOf, Integer::valueOf);
        assertEquals("17", view.get(1, 2, 3));
        view.set(1, 2, 3, "23");
        assertEquals(Integer.valueOf(23), source.get(1, 2, 3));
    }

    @Test
    public void safeWritesDropCoordinatesOutsideTheHunk() {
        Hunk<String> hunk = Hunk.newArrayHunk(2, 3, 4);
        hunk.set(-1, 0, 0, "outside");
        hunk.set(2, 0, 0, "outside");
        hunk.set(0, 3, 0, "outside");
        hunk.set(0, 0, 4, "outside");
        hunk.iterateSync((x, y, z, value) -> assertNull(value));
        hunk.set(1, 2, 3, "inside");
        assertEquals("inside", hunk.get(1, 2, 3));
    }

    @Test(expected = IllegalStateException.class)
    public void readOnlyViewsRejectWrites() {
        Hunk.newArrayHunk(2, 2, 2).readOnly().setRaw(0, 0, 0, "blocked");
    }

    @Test
    public void composedViewsPreserveRoutingAndWriteTracking() {
        Hunk<String> source = Hunk.newAtomicHunk(4, 4, 4);
        source.setRaw(1, 2, 3, "source");
        AtomicBoolean written = new AtomicBoolean();
        AtomicInteger notifications = new AtomicInteger();
        Hunk<String> view = source.trackWrite(written).listen((x, y, z, value) -> notifications.incrementAndGet()).synchronize();
        assertEquals("source", view.getRaw(1, 2, 3));
        assertFalse(written.get());
        assertFalse(view.isAtomic());
        view.setRaw(1, 2, 3, "changed");
        assertTrue(written.get());
        assertEquals(1, notifications.get());
        assertEquals("changed", source.getRaw(1, 2, 3));
        assertEquals("changed", source.invertY().getRaw(1, 2, 3));
        source.invertY().setRaw(0, 0, 0, "mirrored");
        assertEquals("mirrored", source.getRaw(0, 3, 0));
        assertEquals("changed", source.drift(1, 2, 3).getRaw(0, 0, 0));
        Hunk<String> output = Hunk.newArrayHunk(4, 4, 4);
        Hunk<String> fringe = Hunk.fringe(source, output);
        assertEquals("changed", fringe.getRaw(1, 2, 3));
        fringe.setRaw(1, 2, 3, "output");
        assertEquals("output", output.getRaw(1, 2, 3));
        assertEquals("changed", source.getRaw(1, 2, 3));
    }

    @Test(timeout = 10000)
    public void entryCountTraversalIncludesUnpopulatedCoordinates() {
        Hunk<String> hunk = Hunk.newArrayHunk(8, 8, 8);
        hunk.setRaw(1, 2, 3, "stored");
        AtomicInteger dispatches = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertEquals(512, hunk.getNonNullEntries(size -> {
                dispatches.incrementAndGet();
                return new BurstExecutorSupport(executor, size);
            }));
        } finally {
            executor.shutdownNow();
        }
        assertTrue(dispatches.get() > 0);
    }

    @Test(timeout = 10000)
    public void copiedSectionsUseTheCallerExecutorAndMergeEveryCell() {
        Hunk<Integer> hunk = Hunk.newArrayHunk(8, 8, 8);
        Thread caller = Thread.currentThread();
        AtomicInteger sections = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            hunk.compute3D(8, size -> new BurstExecutorSupport(executor, size), (x, y, z, section) -> {
                assertNotSame(caller, Thread.currentThread());
                sections.incrementAndGet();
                section.iterateSync((dx, dy, dz) -> section.setRaw(dx, dy, dz,
                        (x + dx) + 8 * (y + dy) + 64 * (z + dz)));
            });
        } finally {
            executor.shutdownNow();
        }
        assertTrue(sections.get() > 1);
        hunk.iterateSync((x, y, z, value) -> assertEquals(Integer.valueOf(x + 8 * y + 64 * z), value));
    }

    @Test(timeout = 10000)
    public void atomicSectionsWriteDirectlyIntoTheOriginalHunk() {
        Hunk<Integer> hunk = new AtomicHunk<>(8, 8, 8);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            hunk.compute2D(4, size -> new BurstExecutorSupport(executor, size), (x, y, z, section) -> section.fill(9));
        } finally {
            executor.shutdownNow();
        }
        hunk.iterateSync((x, y, z, value) -> assertEquals(Integer.valueOf(9), value));
    }
}
