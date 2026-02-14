package art.arcane.volmlib.integration;

import java.util.Map;

public record IntegrationMetricDescriptor(
        String key,
        IntegrationMetricType type,
        String unit,
        Map<String, String> tags
) {
    public IntegrationMetricDescriptor {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Metric key cannot be blank");
        }

        if (type == null) {
            throw new IllegalArgumentException("Metric type cannot be null");
        }

        unit = unit == null ? "" : unit.trim();
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }
}
