package art.arcane.volmlib.util.scheduling;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Window semantics plus the monotonic-clock seam. The limiter used to take a wall-clock timestamp
 * from the caller, which made it vulnerable to NTP steps in both directions; the window is now
 * driven by the limiter's own monotonic clock.
 */
public class SlidingWindowRateLimiterTest {
    @Test
    public void capsAcquisitionsWithinOneSecondWindow() {
        AtomicLong clock = new AtomicLong();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000L, clock::get);

        assertTrue(limiter.tryAcquire(3));
        clock.set(100L);
        assertTrue(limiter.tryAcquire(3));
        clock.set(200L);
        assertTrue(limiter.tryAcquire(3));
        clock.set(300L);
        assertFalse(limiter.tryAcquire(3));
        clock.set(999L);
        assertFalse(limiter.tryAcquire(3));
    }

    @Test
    public void windowSlidesInsteadOfResetting() {
        AtomicLong clock = new AtomicLong();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000L, clock::get);

        assertTrue(limiter.tryAcquire(2));
        clock.set(900L);
        assertTrue(limiter.tryAcquire(2));
        clock.set(950L);
        assertFalse(limiter.tryAcquire(2));
        clock.set(1000L);
        assertTrue(limiter.tryAcquire(2));
        clock.set(1100L);
        assertFalse(limiter.tryAcquire(2));
        clock.set(1900L);
        assertTrue(limiter.tryAcquire(2));
    }

    @Test
    public void limitChangeAppliesImmediately() {
        AtomicLong clock = new AtomicLong();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000L, clock::get);

        assertTrue(limiter.tryAcquire(1));
        clock.set(10L);
        assertFalse(limiter.tryAcquire(1));
        clock.set(20L);
        assertTrue(limiter.tryAcquire(2));
    }

    @Test
    public void expiryIsExactlyTheWindowLength() {
        AtomicLong clock = new AtomicLong(500L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000L, clock::get);

        assertTrue(limiter.tryAcquire(1));
        clock.set(1499L);
        assertFalse(limiter.tryAcquire(1));
        clock.set(1500L);
        assertTrue(limiter.tryAcquire(1));
    }

    @Test
    public void windowLengthIsClampedToAtLeastOneMillisecond() {
        AtomicLong clock = new AtomicLong();
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(0L, clock::get);

        assertTrue(limiter.tryAcquire(1));
        assertFalse(limiter.tryAcquire(1));
        clock.set(1L);
        assertTrue(limiter.tryAcquire(1));
    }

    @Test
    public void theDefaultClockNeverExpiresTheWindowEarly() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(60_000L);

        assertTrue(limiter.tryAcquire(2));
        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire(2));
    }

    @Test
    public void aClockThatStandsStillNeverExpiresQueuedAcquisitions() {
        // The old defect in its clearest form: a backwards wall-clock step made every queued
        // acquisition look like it arrived in the future and parked the limiter. A monotonic clock
        // only ever stands still or moves forward, and standing still simply holds the window.
        AtomicLong clock = new AtomicLong(1_000_000L);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(1000L, clock::get);

        assertTrue(limiter.tryAcquire(1));
        for (int attempt = 0; attempt < 100; attempt++) {
            assertFalse(limiter.tryAcquire(1));
        }

        clock.addAndGet(1000L);
        assertTrue(limiter.tryAcquire(1));
    }
}
