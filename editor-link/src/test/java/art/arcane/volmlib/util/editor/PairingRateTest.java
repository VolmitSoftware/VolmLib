package art.arcane.volmlib.util.editor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.volmlib.util.editor.EditorFixtures.MutableClock;
import art.arcane.volmlib.util.editor.transport.EditorResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PairingRateTest {
    private static final Instant MINUTE = Instant.parse("2026-11-19T10:00:00Z");
    private final MutableClock clock = new MutableClock(MINUTE.plusSeconds(15));
    private final AtomicInteger limit = new AtomicInteger(3);
    private final PairingRate rate = new PairingRate(clock, limit::get);

    @Test
    void pairing_pastLimit_refusedRateLimited() throws EditorRefusal {
        for (int attempt = 0; attempt < 3; attempt++) rate.pairing("203.0.113.9");
        EditorRefusal refused = assertThrows(EditorRefusal.class, () -> rate.pairing("203.0.113.9"));
        assertEquals(EditorRefusal.Code.RATE_LIMITED, refused.code());
        assertDoesNotThrow(() -> rate.pairing("203.0.113.10"));
    }

    @Test
    void pairing_ipv6InOneSlash64_sharesAWindow() throws EditorRefusal {
        rate.pairing("2001:db8:1:2::1");
        rate.pairing("2001:db8:1:2::ffff");
        rate.pairing("[2001:db8:1:2:aaaa::1]");
        assertThrows(EditorRefusal.class, () -> rate.pairing("2001:db8:1:2:ffff:ffff:ffff:ffff"));
        assertDoesNotThrow(() -> rate.pairing("2001:db8:1:3::1"));
    }

    @Test
    void guard_failedAndPairingAttempts_countTogether() throws EditorRefusal {
        rate.guard("198.51.100.4");
        rate.failed("198.51.100.4");
        rate.failed("198.51.100.4");
        rate.guard("198.51.100.4");
        rate.pairing("198.51.100.4");
        assertEquals(EditorRefusal.Code.RATE_LIMITED, assertThrows(EditorRefusal.class, () -> rate.guard("198.51.100.4")).code());
        assertThrows(EditorRefusal.class, () -> rate.pairing("198.51.100.4"));
        rate.guard("198.51.100.5");
    }

    @Test
    void guard_reconnectsWithoutFailures_neverCount() throws EditorRefusal {
        for (int attempt = 0; attempt < 10; attempt++) rate.guard("198.51.100.4");
        for (int attempt = 0; attempt < 3; attempt++) rate.pairing("198.51.100.4");
    }

    @Test
    void retryAfter_limitedMidMinute_isWhatIsLeftOfIt() throws EditorRefusal {
        for (int attempt = 0; attempt < 3; attempt++) rate.pairing("203.0.113.9");
        EditorRefusal refused = assertThrows(EditorRefusal.class, () -> rate.pairing("203.0.113.9"));
        assertEquals(45_000, refused.retryAfterMillis());
        assertTrue(refused.getMessage().contains("Try again in 45 s."), refused.getMessage());
        EditorResponse response = refused.response();
        assertEquals(429, response.status());
        assertEquals("45", response.headers().get("Retry-After"));
        assertEquals("rate-limited", response.body().orElseThrow().getAsJsonObject().getAsJsonObject("error").get("code").getAsString());
        assertEquals(45_000, response.body().orElseThrow().getAsJsonObject().getAsJsonObject("error").get("retryAfterMillis").getAsLong());
    }

    @Test
    void window_nextMinute_resets() throws EditorRefusal {
        for (int attempt = 0; attempt < 3; attempt++) rate.pairing("203.0.113.9");
        rate.failed("198.51.100.4");
        rate.failed("198.51.100.4");
        rate.failed("198.51.100.4");
        clock.advance(Duration.ofSeconds(44));
        assertThrows(EditorRefusal.class, () -> rate.pairing("203.0.113.9"));
        assertThrows(EditorRefusal.class, () -> rate.guard("198.51.100.4"));
        clock.advance(Duration.ofSeconds(1));
        for (int attempt = 0; attempt < 3; attempt++) rate.pairing("203.0.113.9");
        rate.guard("198.51.100.4");
    }

    @Test
    void limit_changedSetting_appliesAtOnce() throws EditorRefusal {
        limit.set(1);
        rate.pairing("203.0.113.9");
        assertThrows(EditorRefusal.class, () -> rate.pairing("203.0.113.9"));
        limit.set(5);
        rate.pairing("203.0.113.9");
    }
}
