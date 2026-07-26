package art.arcane.volmlib.util.bukkit.papi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlaceholderSnapshotTest {
    @Test
    public void get_isNullUntilTheOwningThreadPublishes() {
        PlaceholderSnapshot<String> snapshot = new PlaceholderSnapshot<>();

        assertNull(snapshot.get());
        assertFalse(snapshot.isPresent());
        assertEquals(0L, snapshot.publishedAtMs());
        assertEquals(PlaceholderValues.FALSE, snapshot.available());
    }

    @Test
    public void publish_replacesTheValueAndStampsThePublishTime() {
        PlaceholderSnapshot<String> snapshot = new PlaceholderSnapshot<>();
        long before = System.currentTimeMillis();

        snapshot.publish("first");

        assertSame("first", snapshot.get());
        assertTrue(snapshot.isPresent());
        assertEquals(PlaceholderValues.TRUE, snapshot.available());
        assertTrue(snapshot.publishedAtMs() >= before);

        snapshot.publish("second");

        assertSame("second", snapshot.get());
    }

    @Test
    public void publish_nullClearsTheSnapshotBackToUnavailable() {
        PlaceholderSnapshot<String> snapshot = new PlaceholderSnapshot<>();
        snapshot.publish("value");
        snapshot.publish(null);

        assertNull(snapshot.get());
        assertEquals(PlaceholderValues.FALSE, snapshot.available());
    }
}
