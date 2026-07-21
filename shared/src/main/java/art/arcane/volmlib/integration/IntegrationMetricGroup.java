package art.arcane.volmlib.integration;

import java.util.LinkedHashMap;
import java.util.Map;

public record IntegrationMetricGroup(
        String scopeKind,
        String scopeId,
        String label,
        Map<String, String> tags,
        Map<String, IntegrationMetricSample> samples
) {
    public IntegrationMetricGroup {
        if (scopeKind == null || scopeKind.isBlank()) {
            throw new IllegalArgumentException("Metric group scope kind cannot be blank");
        }
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("Metric group scope id cannot be blank");
        }

        scopeKind = scopeKind.trim();
        scopeId = scopeId.trim();
        label = label == null || label.isBlank() ? scopeId : label.trim();
        tags = tags == null ? Map.of() : Map.copyOf(tags);
        samples = validatedSamples(samples);
    }

    private static Map<String, IntegrationMetricSample> validatedSamples(
            Map<String, IntegrationMetricSample> samples
    ) {
        if (samples == null || samples.isEmpty()) {
            return Map.of();
        }

        Map<String, IntegrationMetricSample> validated = new LinkedHashMap<>(samples.size());
        for (Map.Entry<String, IntegrationMetricSample> entry : samples.entrySet()) {
            String key = entry.getKey();
            IntegrationMetricSample sample = entry.getValue();
            if (key == null || key.isBlank() || sample == null) {
                throw new IllegalArgumentException("Metric group samples cannot contain blank keys or null values");
            }
            if (!key.equals(sample.descriptor().key())) {
                throw new IllegalArgumentException("Metric group sample key does not match its descriptor: " + key);
            }
            validated.put(key, sample);
        }
        return Map.copyOf(validated);
    }
}
