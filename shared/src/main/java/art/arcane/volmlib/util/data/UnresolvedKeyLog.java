package art.arcane.volmlib.util.data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class UnresolvedKeyLog {
    private static final long MIN_INTERVAL_MS = 1000L;

    private final String label;
    private final long summaryIntervalMs;
    private final Set<String> seen = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingDistinct = new AtomicInteger();
    private final AtomicLong nextSummaryMs;

    public UnresolvedKeyLog(String label, long summaryIntervalMs) {
        this.label = label;
        this.summaryIntervalMs = Math.max(MIN_INTERVAL_MS, summaryIntervalMs);
        this.nextSummaryMs = new AtomicLong(System.currentTimeMillis() + this.summaryIntervalMs);
    }

    public boolean firstOccurrence(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        if (seen.contains(key)) {
            return false;
        }
        if (seen.add(key.intern())) {
            pendingDistinct.incrementAndGet();
            return true;
        }
        return false;
    }

    public String pollSummary() {
        long now = System.currentTimeMillis();
        long due = nextSummaryMs.get();
        if (now < due) {
            return null;
        }
        if (!nextSummaryMs.compareAndSet(due, now + summaryIntervalMs)) {
            return null;
        }
        int count = pendingDistinct.getAndSet(0);
        if (count <= 0) {
            return null;
        }
        return label + ": " + count + " new unresolved key(s) since last summary (" + seen.size() + " distinct total)";
    }

    public int distinctCount() {
        return seen.size();
    }

    public void reset() {
        seen.clear();
        pendingDistinct.set(0);
        nextSummaryMs.set(System.currentTimeMillis() + summaryIntervalMs);
    }
}
