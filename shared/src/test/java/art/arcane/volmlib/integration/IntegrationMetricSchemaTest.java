package art.arcane.volmlib.integration;

import org.junit.Test;

import java.util.Set;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IntegrationMetricSchemaTest {
    @Test
    public void createsAdaptAbilityDetailMetricsWithIdentityAndUnits() {
        Set<String> keys = IntegrationMetricSchema.adaptAbilityDetailKeys("Agility Wall Jump");
        String timingKey = "adapt.ability-detail.agility-wall-jump.execution-timing-ms";

        assertTrue(keys.contains(timingKey));
        assertTrue(IntegrationMetricSchema.isAdaptAbilityDetailKey(timingKey));
        assertEquals("agility-wall-jump", IntegrationMetricSchema.adaptAbilityId(timingKey));
        assertEquals(IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS, IntegrationMetricSchema.adaptAbilitySignal(timingKey));

        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(timingKey);
        assertEquals(IntegrationMetricType.DOUBLE, descriptor.type());
        assertEquals("ms-per-minute", descriptor.unit());
        assertEquals("agility-wall-jump", descriptor.tags().get("ability"));
        assertEquals("ability-detail", descriptor.tags().get("domain"));
        assertEquals("event-tick-guarded-callback-inclusive", descriptor.tags().get("coverage"));
    }

    @Test
    public void rejectsMalformedAdaptAbilityDetailKeys() {
        assertFalse(IntegrationMetricSchema.isAdaptAbilityDetailKey("adapt.ability-detail..timing-ms"));
        assertFalse(IntegrationMetricSchema.isAdaptAbilityDetailKey("adapt.ability-detail.wall-jump.unknown"));
        assertFalse(IntegrationMetricSchema.isAdaptAbilityDetailKey("adapt.ability-detail.Wall Jump.execution-timing-ms"));
    }

    @Test
    public void exposesCompleteIrisEngineAndWorldSchemaWithoutVestigialMetrics() {
        Set<String> irisKeys = IntegrationMetricSchema.irisKeys();

        assertEquals(69, irisKeys.size());
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_WORLD_COUNT));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_USAGE));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT));
        assertTrue(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_LOADED_CHUNKS));
        assertFalse(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_CACHE_COUNT));
        assertFalse(irisKeys.contains("iris.chunk-stream-ms"));
        assertFalse(irisKeys.contains("iris.biome-cache-hit-rate"));
    }

    @Test
    public void metricGroupsAreImmutableAndRejectDescriptorMismatches() {
        long now = System.currentTimeMillis();
        String key = IntegrationMetricSchema.IRIS_LOADED_CHUNKS;
        IntegrationMetricSample sample = IntegrationMetricSample.available(
                IntegrationMetricSchema.descriptor(key),
                12D,
                now
        );
        IntegrationMetricGroup group = new IntegrationMetricGroup(
                "world",
                "minecraft:overworld",
                "world",
                Map.of("plugin", "iris"),
                Map.of(key, sample)
        );

        assertEquals(12D, group.samples().get(key).numericValue(), 0D);
        assertThrows(UnsupportedOperationException.class, () -> group.samples().put(key, sample));
        assertThrows(IllegalArgumentException.class, () -> new IntegrationMetricGroup(
                "world",
                "minecraft:overworld",
                "world",
                Map.of(),
                Map.of(IntegrationMetricSchema.IRIS_PREGEN_QUEUE, sample)
        ));
    }

    @Test
    public void samplesRejectNonFiniteAndFractionalIntegralValues() {
        assertThrows(IllegalArgumentException.class, () -> IntegrationMetricSample.available(
                IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_ENTITY_SATURATION),
                Double.NaN,
                1L
        ));
        assertThrows(IllegalArgumentException.class, () -> IntegrationMetricSample.available(
                IntegrationMetricSchema.descriptor(IntegrationMetricSchema.IRIS_LOADED_CHUNKS),
                1.5D,
                1L
        ));
    }
}
