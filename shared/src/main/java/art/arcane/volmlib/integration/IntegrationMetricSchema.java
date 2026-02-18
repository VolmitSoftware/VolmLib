package art.arcane.volmlib.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class IntegrationMetricSchema {
    public static final String IRIS_CHUNK_STREAM_MS = "iris.chunk-stream-ms";
    public static final String IRIS_PREGEN_QUEUE = "iris.pregen-queue";
    public static final String IRIS_BIOME_CACHE_HIT_RATE = "iris.biome-cache-hit-rate";

    public static final String ADAPT_SESSION_LOAD = "adapt.session-load";
    public static final String ADAPT_ABILITY_OPS = "adapt.ability-ops";
    public static final String ADAPT_ABILITY_CHECK_OPS = "adapt.ability-check-ops";
    public static final String ADAPT_ABILITY_CHECK_OPS_TICK = "adapt.ability-check-ops-tick";
    public static final String ADAPT_WORLD_POLICY_LATENCY = "adapt.world-policy-latency";

    private static final Map<String, IntegrationMetricDescriptor> DESCRIPTORS = buildDescriptors();

    private IntegrationMetricSchema() {
    }

    public static IntegrationMetricDescriptor descriptor(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Metric key cannot be blank");
        }

        IntegrationMetricDescriptor known = DESCRIPTORS.get(key);
        if (known != null) {
            return known;
        }

        return new IntegrationMetricDescriptor(
                key,
                IntegrationMetricType.DOUBLE,
                "",
                Map.of("origin", "unknown")
        );
    }

    public static Set<IntegrationMetricDescriptor> descriptors() {
        return Set.copyOf(DESCRIPTORS.values());
    }

    public static Set<String> allKeys() {
        return Set.copyOf(DESCRIPTORS.keySet());
    }

    public static Set<String> irisKeys() {
        return Set.of(IRIS_CHUNK_STREAM_MS, IRIS_PREGEN_QUEUE, IRIS_BIOME_CACHE_HIT_RATE);
    }

    public static Set<String> adaptKeys() {
        return Set.of(ADAPT_SESSION_LOAD, ADAPT_ABILITY_OPS, ADAPT_ABILITY_CHECK_OPS, ADAPT_ABILITY_CHECK_OPS_TICK, ADAPT_WORLD_POLICY_LATENCY);
    }

    private static Map<String, IntegrationMetricDescriptor> buildDescriptors() {
        Map<String, IntegrationMetricDescriptor> descriptors = new LinkedHashMap<>();

        descriptors.put(IRIS_CHUNK_STREAM_MS, new IntegrationMetricDescriptor(
                IRIS_CHUNK_STREAM_MS,
                IntegrationMetricType.DOUBLE,
                "ms",
                Map.of("plugin", "iris", "domain", "generation")
        ));
        descriptors.put(IRIS_PREGEN_QUEUE, new IntegrationMetricDescriptor(
                IRIS_PREGEN_QUEUE,
                IntegrationMetricType.LONG,
                "chunks",
                Map.of("plugin", "iris", "domain", "generation")
        ));
        descriptors.put(IRIS_BIOME_CACHE_HIT_RATE, new IntegrationMetricDescriptor(
                IRIS_BIOME_CACHE_HIT_RATE,
                IntegrationMetricType.DOUBLE,
                "ratio",
                Map.of("plugin", "iris", "domain", "cache")
        ));

        descriptors.put(ADAPT_SESSION_LOAD, new IntegrationMetricDescriptor(
                ADAPT_SESSION_LOAD,
                IntegrationMetricType.DOUBLE,
                "percent",
                Map.of("plugin", "adapt", "domain", "runtime")
        ));
        descriptors.put(ADAPT_ABILITY_OPS, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_OPS,
                IntegrationMetricType.DOUBLE,
                "ops-per-minute",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "successful-checks")
        ));
        descriptors.put(ADAPT_ABILITY_CHECK_OPS, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_CHECK_OPS,
                IntegrationMetricType.DOUBLE,
                "ops-per-minute",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "all-checks")
        ));
        descriptors.put(ADAPT_ABILITY_CHECK_OPS_TICK, new IntegrationMetricDescriptor(
                ADAPT_ABILITY_CHECK_OPS_TICK,
                IntegrationMetricType.DOUBLE,
                "ops-per-tick",
                Map.of("plugin", "adapt", "domain", "ability", "signal", "all-checks-per-tick")
        ));
        descriptors.put(ADAPT_WORLD_POLICY_LATENCY, new IntegrationMetricDescriptor(
                ADAPT_WORLD_POLICY_LATENCY,
                IntegrationMetricType.DOUBLE,
                "ms",
                Map.of("plugin", "adapt", "domain", "policy")
        ));

        return Map.copyOf(descriptors);
    }
}
