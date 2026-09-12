package art.arcane.volmlib.util.hunk;

import art.arcane.volmlib.util.hunk.storage.MappedHunk;
import art.arcane.volmlib.util.hunk.storage.MappedSyncHunk;
import art.arcane.volmlib.util.matter.slices.StringMatter;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class MappedHunkSemanticsTest {
    @Test
    public void genericMappedIterationVisitsEmptyCellsInCoordinateOrder() throws IOException {
        List<Hunk<String>> hunks = List.of(Hunk.newMappedHunk(2, 3, 2), Hunk.newMappedHunkSynced(2, 3, 2));
        for (Hunk<String> hunk : hunks) {
            hunk.setRaw(1, 2, 1, "stored");
            List<String> visits = new ArrayList<>();
            hunk.iterateSync((x, y, z, value) -> visits.add(x + "," + y + "," + z + "=" + value));
            assertEquals(12, visits.size());
            assertEquals("0,0,0=null", visits.get(0));
            assertEquals("0,0,1=null", visits.get(1));
            assertEquals("1,2,1=stored", visits.get(11));
            List<String> ioVisits = new ArrayList<>();
            hunk.iterateSyncIO((x, y, z, value) -> ioVisits.add(x + "," + y + "," + z + "=" + value));
            assertEquals(visits, ioVisits);
        }
    }

    @Test
    public void genericMappedEmptyFillsWithTheSuppliedValue() {
        List<Hunk<String>> hunks = List.of(Hunk.newMappedHunk(2, 3, 2), Hunk.newMappedHunkSynced(2, 3, 2));
        for (Hunk<String> hunk : hunks) {
            assertFalse(hunk.isEmpty());
            hunk.setRaw(0, 0, 0, "old");
            hunk.empty("replacement");
            assertEquals(12, hunk.getEntryCount());
            hunk.iterateSync((x, y, z, value) -> assertEquals("replacement", value));
            hunk.empty(null);
            assertEquals(0, hunk.getEntryCount());
            assertFalse(hunk.isEmpty());
        }
    }

    @Test
    public void explicitSparseIterationAndClearVisitOnlyStoredValues() throws IOException {
        MappedHunk<String> hunk = new MappedHunk<>(2, 3, 2);
        hunk.setRaw(1, 2, 1, "stored");
        List<String> visits = new ArrayList<>();
        hunk.iterateEntriesSync((x, y, z, value) -> visits.add(x + "," + y + "," + z + "=" + value));
        assertEquals(List.of("1,2,1=stored"), visits);
        AtomicInteger ioCount = new AtomicInteger();
        hunk.iterateEntriesSyncIO((x, y, z, value) -> ioCount.incrementAndGet());
        assertEquals(1, ioCount.get());
        hunk.clear();
        assertEquals(0, hunk.getEntryCount());
        assertNull(hunk.getRaw(1, 2, 1));
    }

    @Test
    public void synchronizedSparseIterationAndClearVisitOnlyStoredValues() throws IOException {
        MappedSyncHunk<String> hunk = new MappedSyncHunk<>(2, 3, 2);
        hunk.setRaw(1, 2, 1, "stored");
        List<String> visits = new ArrayList<>();
        hunk.iterateEntriesSync((x, y, z, value) -> visits.add(x + "," + y + "," + z + "=" + value));
        assertEquals(List.of("1,2,1=stored"), visits);
        AtomicInteger ioCount = new AtomicInteger();
        hunk.iterateEntriesSyncIO((x, y, z, value) -> ioCount.incrementAndGet());
        assertEquals(1, ioCount.get());
        hunk.clear();
        assertEquals(0, hunk.getEntryCount());
        assertNull(hunk.getRaw(1, 2, 1));
    }

    @Test(expected = IOException.class)
    public void sparseIoIterationPropagatesWriteFailures() throws IOException {
        MappedHunk<String> hunk = new MappedHunk<>(2, 3, 2);
        hunk.setRaw(1, 2, 1, "stored");
        hunk.iterateEntriesSyncIO((x, y, z, value) -> {
            throw new IOException("Write failed");
        });
    }

    @Test
    public void matterRetainsBoundedAccessAndSparseStorageOperations() {
        StringMatter matter = new StringMatter(16, 32, 16);
        matter.set(-1, 0, 0, "outside");
        matter.set(16, 0, 0, "outside");
        matter.set(2, 3, 4, "stored");
        assertNull(matter.get(-1, 0, 0));
        assertNull(matter.get(16, 0, 0));
        assertEquals("stored", matter.get(2, 3, 4));
        assertEquals(1, matter.getEntryCount());
        AtomicInteger visits = new AtomicInteger();
        matter.iterateSync((x, y, z, value) -> visits.incrementAndGet());
        assertEquals(1, visits.get());
        matter.empty("ignored");
        assertEquals(0, matter.getEntryCount());
        assertNull(matter.get(2, 3, 4));
    }
}
