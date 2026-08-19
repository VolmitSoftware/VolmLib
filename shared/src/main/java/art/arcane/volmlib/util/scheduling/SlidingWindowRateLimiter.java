package art.arcane.volmlib.util.scheduling;

import java.util.ArrayDeque;

public final class SlidingWindowRateLimiter {
    private final long windowMs;
    private final ArrayDeque<Long> acquisitions = new ArrayDeque<>();

    public SlidingWindowRateLimiter() {
        this(1000L);
    }

    public SlidingWindowRateLimiter(long windowMs) {
        this.windowMs = Math.max(1L, windowMs);
    }

    public synchronized boolean tryAcquire(long nowMs, int limit) {
        while (!acquisitions.isEmpty() && nowMs - acquisitions.peekFirst() >= windowMs) {
            acquisitions.pollFirst();
        }
        if (acquisitions.size() >= limit) {
            return false;
        }
        acquisitions.addLast(nowMs);
        return true;
    }
}
