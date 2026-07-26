package art.arcane.volmlib.util.bukkit.papi;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class PlayerSnapshotStoreTest {
    private static final UUID ONLINE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ABSENT = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    public void get_isNullForAPlayerWithNoPublishedSnapshot() {
        PlayerSnapshotStore<String> store = new PlayerSnapshotStore<>();
        store.publish(ONLINE, "level=7");

        assertSame("level=7", store.get(ONLINE));
        assertNull(store.get(ABSENT));
        assertNull(store.get(null));
        assertEquals(PlaceholderValues.FALSE, store.available(ABSENT));
        assertEquals(PlaceholderValues.TRUE, store.available(ONLINE));
    }

    @Test
    public void evictAfterGrace_keepsServingInsideTheWindowAndStopsAfterIt() {
        PlayerSnapshotStore<String> store = new PlayerSnapshotStore<>();
        store.publish(ONLINE, "level=7");
        store.evictAfterGrace(ONLINE, PlayerSnapshotStore.DEFAULT_GRACE_MS);

        assertSame("level=7", store.get(ONLINE));

        store.evictAfterGrace(ONLINE, 1L);
        waitPastGrace();

        assertNull(store.get(ONLINE));
        assertEquals(PlaceholderValues.FALSE, store.available(ONLINE));
    }

    @Test
    public void evictAfterGrace_withNoGraceDropsImmediately() {
        PlayerSnapshotStore<String> store = new PlayerSnapshotStore<>();
        store.publish(ONLINE, "level=7");
        store.evictAfterGrace(ONLINE, 0L);

        assertNull(store.get(ONLINE));
    }

    @Test
    public void publish_afterGraceStartedRestoresTheEntryPermanently() {
        PlayerSnapshotStore<String> store = new PlayerSnapshotStore<>();
        store.publish(ONLINE, "level=7");
        store.evictAfterGrace(ONLINE, 1L);
        store.publish(ONLINE, "level=8");
        waitPastGrace();

        assertSame("level=8", store.get(ONLINE));
    }

    @Test
    public void publishNullAndClearRemoveEntries() {
        PlayerSnapshotStore<String> store = new PlayerSnapshotStore<>();
        store.publish(ONLINE, "level=7");
        store.publish(ONLINE, null);

        assertNull(store.get(ONLINE));

        store.publish(ONLINE, "level=9");
        store.clear();

        assertNull(store.get(ONLINE));
    }

    private static void waitPastGrace() {
        long deadline = System.currentTimeMillis() + 2L;

        while (System.currentTimeMillis() < deadline) {
            Thread.onSpinWait();
        }
    }
}
