package art.arcane.volmlib.integration;

import java.util.Map;
import java.util.Objects;

public record IntegrationMetricSnapshot(long generation, long capturedAtMs,
                                        Map<String, IntegrationMetricSample> samples) {
    public IntegrationMetricSnapshot {
        if (generation < 0 || capturedAtMs < 0) {
            throw new IllegalArgumentException("Snapshot generation and capture time must be nonnegative");
        }
        samples = Map.copyOf(Objects.requireNonNull(samples, "samples"));
        for (Map.Entry<String, IntegrationMetricSample> entry : samples.entrySet()) {
            IntegrationMetricSample sample = entry.getValue();
            if (!entry.getKey().equals(sample.descriptor().key())) {
                throw new IllegalArgumentException("Snapshot sample key must match its descriptor");
            }
            if (sample.sampledAtMs() < 0 || sample.sampledAtMs() > capturedAtMs) {
                throw new IllegalArgumentException("Snapshot sample timestamp must not exceed capture time");
            }
        }
    }
}
