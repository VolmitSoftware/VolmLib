package art.arcane.volmlib.integration;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IntegrationMetricPublisherTest {
    @Test
    public void demandWarmsWithoutCollectingAndReadsPreserveCaptureTime() {
        IntegrationMetricPublisher publisher = new IntegrationMetricPublisher(4, 1000);
        assertTrue(publisher.snapshotMetrics(Set.of("test.value"), 100).samples().isEmpty());
        IntegrationMetricPublisher.Demand demand = publisher.demandedKeys(100);
        Map<String, IntegrationMetricSample> captured = new HashMap<>();
        captured.put("test.value", sample("test.value", 7, 125));
        assertTrue(publisher.publish(demand, 150, captured));
        captured.clear();

        IntegrationMetricSnapshot first = publisher.snapshotMetrics(Set.of("test.value"), 200);
        IntegrationMetricSnapshot repeated = publisher.snapshotMetrics(Set.of("test.value"), 500);
        assertEquals(first, repeated);
        assertEquals(1, first.generation());
        assertEquals(150, first.capturedAtMs());
        assertEquals(125, first.samples().get("test.value").sampledAtMs());
        assertThrows(UnsupportedOperationException.class, () -> first.samples().clear());
    }

    @Test
    public void clearRejectsOldCapturesAndOlderCaptureCannotReplaceNewerPublication() {
        IntegrationMetricPublisher publisher = new IntegrationMetricPublisher(2, 1000);
        publisher.snapshotMetrics(Set.of("test.value"), 0);
        IntegrationMetricPublisher.Demand old = publisher.demandedKeys(0);
        publisher.clear();
        publisher.snapshotMetrics(Set.of("test.value"), 10);
        IntegrationMetricPublisher.Demand current = publisher.demandedKeys(10);
        assertFalse(publisher.publish(old, 100, Map.of("test.value", sample("test.value", 99, 100))));
        assertTrue(publisher.publish(current, 200, Map.of("test.value", sample("test.value", 2, 200))));
        assertFalse(publisher.publish(current, 150, Map.of("test.value", sample("test.value", 1, 150))));
        assertEquals(2D, publisher.snapshotMetrics(Set.of("test.value"), 250).samples().get("test.value").valueOr(0), 0D);
    }

    @Test
    public void admissionIsBoundedBeforePublicationAndDemandExpires() {
        IntegrationMetricPublisher publisher = new IntegrationMetricPublisher(2, 100);
        assertThrows(IllegalArgumentException.class, () -> publisher.snapshotMetrics(Set.of("a", "b", "c"), 0));
        assertTrue(publisher.demandedKeys(0).keys().isEmpty());
        publisher.snapshotMetrics(Set.of("a"), 0);
        publisher.snapshotMetrics(Set.of("b"), 1);
        publisher.snapshotMetrics(Set.of("a"), 2);
        publisher.snapshotMetrics(Set.of("c"), 3);
        assertEquals(Set.of("a", "c"), publisher.demandedKeys(3).keys());
        assertEquals(1, publisher.evictions());
        IntegrationMetricPublisher.Demand demand = publisher.demandedKeys(3);
        assertThrows(IllegalArgumentException.class,
            () -> publisher.publish(demand, 4, Map.of("unrequested", sample("unrequested", 1, 4))));
        assertTrue(publisher.demandedKeys(104).keys().isEmpty());
    }

    @Test
    public void capacityReloadInvalidatesCaptureWithoutResettingSequence() {
        IntegrationMetricPublisher publisher = new IntegrationMetricPublisher(2, 1000);
        publisher.snapshotMetrics(Set.of("a"), 0);
        IntegrationMetricPublisher.Demand old = publisher.demandedKeys(0);
        publisher.publish(old, 10, Map.of("a", sample("a", 1, 10)));
        long generation = publisher.snapshotMetrics(Set.of("a"), 10).generation();
        publisher.reconfigureCapacity(2);
        assertEquals(generation, publisher.snapshotMetrics(Set.of("a"), 10).generation());
        publisher.reconfigureCapacity(1);
        assertTrue(publisher.snapshotMetrics(Set.of("a"), 20).generation() > generation);
        assertFalse(publisher.publish(old, 30, Map.of("a", sample("a", 2, 30))));
        assertThrows(IllegalArgumentException.class, () -> publisher.snapshotMetrics(Set.of("a", "b"), 30));
        assertThrows(IllegalArgumentException.class, () -> publisher.reconfigureCapacity(0));
        assertTrue(publisher.publish(publisher.demandedKeys(30), 40, Map.of("a", sample("a", 3, 40))));
        assertEquals(3D, publisher.snapshotMetrics(Set.of("a"), 50).samples().get("a").valueOr(0), 0D);
    }

    @Test
    public void snapshotsRejectMismatchedKeysAndImpossibleCaptureTimes() {
        assertThrows(IllegalArgumentException.class,
            () -> new IntegrationMetricSnapshot(-1, 0, Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new IntegrationMetricSnapshot(1, 10, Map.of("a", sample("b", 1, 10))));
        assertThrows(IllegalArgumentException.class,
            () -> new IntegrationMetricSnapshot(1, 10, Map.of("a", sample("a", 1, 11))));
        assertThrows(IllegalArgumentException.class,
            () -> new IntegrationMetricSnapshot(1, 10, Map.of("a", sample("a", 1, -1))));
    }

    @Test
    public void concurrentDemandAndPublicationStayBounded() throws Exception {
        IntegrationMetricPublisher publisher = new IntegrationMetricPublisher(4, 10000);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int workerId = worker;
                tasks.add(executor.submit(() -> {
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    for (int iteration = 0; iteration < 200; iteration++) {
                        String key = "worker." + workerId + "." + iteration;
                        publisher.snapshotMetrics(Set.of(key), iteration);
                        IntegrationMetricPublisher.Demand demand = publisher.demandedKeys(iteration);
                        assertTrue(demand.keys().size() <= 4);
                        Map<String, IntegrationMetricSample> captured = new HashMap<>();
                        for (String requested : demand.keys()) {
                            captured.put(requested, sample(requested, iteration, iteration));
                        }
                        publisher.publish(demand, iteration, captured);
                    }
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertTrue(publisher.evictions() > 0);
        } finally {
            executor.shutdownNow();
        }
    }

    private static IntegrationMetricSample sample(String key, double value, long capturedAt) {
        return IntegrationMetricSample.available(new IntegrationMetricDescriptor(key, IntegrationMetricType.DOUBLE, "", Map.of()), value, capturedAt);
    }
}
