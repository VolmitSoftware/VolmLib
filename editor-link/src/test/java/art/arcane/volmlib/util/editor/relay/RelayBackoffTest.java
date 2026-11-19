package art.arcane.volmlib.util.editor.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RelayBackoffTest {
    private final RelayBackoff backoff = new RelayBackoff(1000, 60_000);

    @Test
    void delay_attempts_doubleFromTheMinimumUpToTheMaximum() {
        assertEquals(1000, backoff.delayMillis(0, 1));
        assertEquals(2000, backoff.delayMillis(1, 1));
        assertEquals(32_000, backoff.delayMillis(5, 1));
        assertEquals(60_000, backoff.delayMillis(6, 1));
        assertEquals(60_000, backoff.delayMillis(Integer.MAX_VALUE, 1));
    }

    @Test
    void delay_unit_drawnFromTheUpperHalf() {
        assertEquals(500, backoff.delayMillis(0, 0));
        assertEquals(750, backoff.delayMillis(0, 0.5));
        assertEquals(30_000, backoff.delayMillis(40, 0));
        assertEquals(30_000, backoff.delayMillis(40, -3));
        assertEquals(60_000, backoff.delayMillis(40, 7));
    }

    @Test
    void constructor_maximumBelowMinimum_refused() {
        assertThrows(IllegalArgumentException.class, () -> new RelayBackoff(10, 5));
        assertThrows(IllegalArgumentException.class, () -> new RelayBackoff(0, 5));
    }
}
