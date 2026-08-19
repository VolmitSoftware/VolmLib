package art.arcane.volmlib.util.scheduling;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Sliding-window rate limiter driven by a monotonic clock.
 *
 * <p>The window deliberately does not run on wall-clock time. An NTP step backwards would make
 * every queued acquisition look like it arrived in the future, parking the limiter until real time
 * caught up; a step forwards would flush the window and let a full burst through. {@link
 * System#nanoTime()} has neither failure mode.
 */
public final class SlidingWindowRateLimiter {
    private final long windowMs;
    private final LongSupplier clock;
    private final ArrayDeque<Long> acquisitions = new ArrayDeque<>();

    public SlidingWindowRateLimiter() {
        this(1000L);
    }

    public SlidingWindowRateLimiter(long windowMs) {
        this(windowMs, SlidingWindowRateLimiter::monotonicMillis);
    }

    /**
     * Seam for tests and for callers that already own a monotonic millisecond timeline.
     *
     * @param monotonicMillisClock must never move backwards
     */
    public SlidingWindowRateLimiter(long windowMs, LongSupplier monotonicMillisClock) {
        this.windowMs = Math.max(1L, windowMs);
        this.clock = monotonicMillisClock;
    }

    public synchronized boolean tryAcquire(int limit) {
        long now = clock.getAsLong();

        while (!acquisitions.isEmpty() && now - acquisitions.peekFirst() >= windowMs) {
            acquisitions.pollFirst();
        }

        if (acquisitions.size() >= limit) {
            return false;
        }

        acquisitions.addLast(now);
        return true;
    }

    private static long monotonicMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }
}
