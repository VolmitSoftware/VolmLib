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
    public void wormholesInFlightMetricCountsPlayerAndEntityTransfers() {
        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(
                IntegrationMetricSchema.WORMHOLES_TRANSFERS_IN_FLIGHT
        );

        assertEquals(IntegrationMetricType.INTEGER, descriptor.type());
        assertEquals("transfers", descriptor.unit());
    }

    @Test
    public void exposesCompleteIrisEngineAndWorldSchemaWithoutVestigialMetrics() {
        Set<String> irisKeys = IntegrationMetricSchema.irisKeys();

        assertEquals(70, irisKeys.size());
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_WORLD_COUNT));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_CACHE_STREAM_3D_USAGE));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT));
        assertTrue(irisKeys.contains(IntegrationMetricSchema.IRIS_PREGEN_REMAINING));
        assertTrue(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_PREGEN_QUEUE));
        assertTrue(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_PREGEN_REMAINING));
        assertTrue(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_LOADED_CHUNKS));
        assertFalse(IntegrationMetricSchema.irisWorldKeys().contains(IntegrationMetricSchema.IRIS_CACHE_COUNT));
        assertFalse(irisKeys.contains("iris.chunk-stream-ms"));
        assertFalse(irisKeys.contains("iris.biome-cache-hit-rate"));
    }

    @Test
    public void exposesGlossSchema() {
        Set<String> glossKeys = IntegrationMetricSchema.glossKeys();
        Set<String> allKeys = IntegrationMetricSchema.allKeys();

        assertEquals(18, glossKeys.size());
        for (String key : glossKeys) {
            assertTrue(key.startsWith("gloss."));
            assertTrue(allKeys.contains(key));
            assertEquals("gloss", IntegrationMetricSchema.descriptor(key).tags().get("plugin"));
        }
        for (String key : allKeys) {
            assertFalse(key.startsWith("holoui."));
        }
        assertFalse(allKeys.contains("gloss.builder-server-running"));

        IntegrationMetricDescriptor sessionHolders = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.GLOSS_SESSION_HOLDERS);
        assertEquals(IntegrationMetricType.INTEGER, sessionHolders.type());
        assertEquals("players", sessionHolders.unit());
        assertEquals("sessions", sessionHolders.tags().get("domain"));

        IntegrationMetricDescriptor tickMs = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.GLOSS_TICK_MS);
        assertEquals(IntegrationMetricType.DOUBLE, tickMs.type());
        assertEquals("ms-per-second", tickMs.unit());

        IntegrationMetricDescriptor bubbles = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.GLOSS_BUBBLES_PER_SECOND);
        assertEquals(IntegrationMetricType.DOUBLE, bubbles.type());
        assertEquals("bubbles-per-second", bubbles.unit());

        IntegrationMetricDescriptor holograms = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.GLOSS_HOLOGRAMS_ACTIVE);
        assertEquals(IntegrationMetricType.INTEGER, holograms.type());
        assertEquals("holograms", holograms.unit());
    }

    @Test
    public void wormholesSchemaPublishesProjectionPlateCost() {
        Set<String> keys = IntegrationMetricSchema.wormholesKeys();

        assertTrue(keys.contains(IntegrationMetricSchema.WORMHOLES_PLATE_BUILDS_PER_SECOND));
        assertTrue(keys.contains(IntegrationMetricSchema.WORMHOLES_PLATE_BYTES));
        assertTrue(keys.contains(IntegrationMetricSchema.WORMHOLES_BLOCK_ENTITIES_PER_SECOND));
        assertTrue(IntegrationMetricSchema.allKeys().containsAll(keys));
        assertEquals(IntegrationMetricType.DOUBLE,
                IntegrationMetricSchema.descriptor(IntegrationMetricSchema.WORMHOLES_PLATE_BUILDS_PER_SECOND).type());
        assertEquals("bytes", IntegrationMetricSchema.descriptor(IntegrationMetricSchema.WORMHOLES_PLATE_BYTES).unit());
        assertEquals("projection",
                IntegrationMetricSchema.descriptor(IntegrationMetricSchema.WORMHOLES_BLOCK_ENTITIES_PER_SECOND).tags().get("domain"));
    }

    @Test
    public void adaptFxPacketsAcceptsAFractionalPerTickAverage() {
        IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_FX_PACKETS_USED);

        assertEquals(IntegrationMetricType.DOUBLE, descriptor.type());
        assertEquals("packets-per-tick", descriptor.unit());
        assertEquals(12.5D, IntegrationMetricSample.available(descriptor, 12.5D, 1L).numericValue(), 0D);
    }

    @Test
    public void exposesShapedPortalsSchema() {
        Set<String> keys = IntegrationMetricSchema.shapedPortalsKeys();

        assertEquals(6, keys.size());
        assertTrue(IntegrationMetricSchema.allKeys().containsAll(keys));
        for (String key : keys) {
            IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(key);
            assertTrue(key.startsWith("shapedportals."));
            assertEquals("shapedportals", descriptor.tags().get("plugin"));
        }

        IntegrationMetricDescriptor managed = IntegrationMetricSchema.descriptor(
                IntegrationMetricSchema.SHAPEDPORTALS_MANAGED_PORTALS
        );
        assertEquals(IntegrationMetricType.INTEGER, managed.type());
        assertEquals("portals", managed.unit());
        assertEquals("portals", managed.tags().get("domain"));

        IntegrationMetricDescriptor success = IntegrationMetricSchema.descriptor(
                IntegrationMetricSchema.SHAPEDPORTALS_CREATION_SUCCESS_PERCENT
        );
        assertEquals(IntegrationMetricType.DOUBLE, success.type());
        assertEquals("percent", success.unit());
        assertEquals("creation", success.tags().get("domain"));
    }

    @Test
    public void exposesFoundationRuntimeSchema() {
        Set<String> keys = IntegrationMetricSchema.foundationKeys();

        assertEquals(10, keys.size());
        assertTrue(IntegrationMetricSchema.allKeys().containsAll(keys));
        for (String key : keys) {
            IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(key);
            assertTrue(key.startsWith("foundation."));
            assertEquals("foundation", descriptor.tags().get("plugin"));
        }

        IntegrationMetricDescriptor failures = IntegrationMetricSchema.descriptor(
                IntegrationMetricSchema.FOUNDATION_PROFILE_SAVE_FAILURES_TOTAL
        );
        assertEquals(IntegrationMetricType.LONG, failures.type());
        assertEquals("failures", failures.unit());
        assertEquals("persistence", failures.tags().get("domain"));

        IntegrationMetricDescriptor entries = IntegrationMetricSchema.descriptor(
                IntegrationMetricSchema.FOUNDATION_WORTH_ENTRIES
        );
        assertEquals(IntegrationMetricType.INTEGER, entries.type());
        assertEquals("items", entries.unit());
        assertEquals("economy", entries.tags().get("domain"));
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
