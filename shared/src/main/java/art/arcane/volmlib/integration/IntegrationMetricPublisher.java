package art.arcane.volmlib.integration;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class IntegrationMetricPublisher {
    private int capacity;
    private final long demandTtlMs;
    private final Map<String, Long> demand = new LinkedHashMap<>(16, 0.75F, true);
    private IntegrationMetricSnapshot publication = new IntegrationMetricSnapshot(0, 0, Map.of());
    private long epoch;
    private long evictions;

    public IntegrationMetricPublisher(int capacity, long demandTtlMs) {
        if (capacity < 1 || demandTtlMs < 1) {
            throw new IllegalArgumentException("Demand capacity and lifetime must be positive");
        }
        this.capacity = capacity;
        this.demandTtlMs = demandTtlMs;
    }

    public synchronized IntegrationMetricSnapshot snapshotMetrics(Set<String> metricKeys, long nowMs) {
        Objects.requireNonNull(metricKeys, "metricKeys");
        if (metricKeys.size() > capacity) {
            throw new IllegalArgumentException("Metric request exceeds demand capacity " + capacity);
        }
        Map<String, IntegrationMetricSample> selected = new LinkedHashMap<>();
        for (String key : metricKeys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Metric keys must not be blank");
            }
            if (!demand.containsKey(key) && demand.size() >= capacity) {
                Iterator<String> oldest = demand.keySet().iterator();
                oldest.next();
                oldest.remove();
                evictions++;
            }
            demand.put(key, nowMs);
            IntegrationMetricSample sample = publication.samples().get(key);
            if (sample != null) {
                selected.put(key, sample);
            }
        }
        return new IntegrationMetricSnapshot(publication.generation(), publication.capturedAtMs(), selected);
    }

    public synchronized Demand demandedKeys(long nowMs) {
        demand.entrySet().removeIf(entry -> nowMs - entry.getValue() > demandTtlMs);
        return new Demand(epoch, Set.copyOf(demand.keySet()));
    }

    public synchronized boolean publish(Demand capturedDemand, long capturedAtMs,
                                        Map<String, IntegrationMetricSample> samples) {
        Objects.requireNonNull(capturedDemand, "capturedDemand");
        Objects.requireNonNull(samples, "samples");
        if (capturedDemand.epoch() != epoch || capturedAtMs < publication.capturedAtMs()) {
            return false;
        }
        if (samples.size() > capacity || !capturedDemand.keys().containsAll(samples.keySet())) {
            throw new IllegalArgumentException("Publication must contain only captured demand within capacity");
        }
        publication = new IntegrationMetricSnapshot(Math.incrementExact(publication.generation()), capturedAtMs, samples);
        return true;
    }

    public synchronized void reconfigureCapacity(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Demand capacity must be positive");
        }
        if (this.capacity == capacity) {
            return;
        }
        this.capacity = capacity;
        clear();
    }

    public synchronized void clear() {
        epoch = Math.incrementExact(epoch);
        demand.clear();
        publication = new IntegrationMetricSnapshot(Math.incrementExact(publication.generation()), 0, Map.of());
    }

    public synchronized long evictions() {
        return evictions;
    }

    public record Demand(long epoch, Set<String> keys) {
        public Demand {
            keys = Set.copyOf(keys);
        }
    }
}
