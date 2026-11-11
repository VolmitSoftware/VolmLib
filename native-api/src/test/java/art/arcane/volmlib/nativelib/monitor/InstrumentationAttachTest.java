package art.arcane.volmlib.nativelib.monitor;

import org.junit.After;
import org.junit.Test;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class InstrumentationAttachTest {
    private static final Logger LOG = Logger.getLogger("InstrumentationAttachTest");
    private final List<String> usedProperties = new ArrayList<>();

    @After
    public void clearProperties() {
        for (String property : usedProperties) {
            System.clearProperty(property);
        }
    }

    @Test
    public void failedAttachIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        InstrumentationAttach attach = new InstrumentationAttach(() -> {
            attempts.incrementAndGet();
            Thread.sleep(200L);
            throw new IllegalStateException("dynamic attach refused");
        }, property("failed"), LOG);

        assertNull(attach.get());
        long start = System.nanoTime();
        assertNull(attach.get());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(1, attempts.get());
        assertTrue("second call took " + elapsedMillis + " ms", elapsedMillis < 50L);
    }

    @Test
    public void nullResultCountsAsFailure() {
        AtomicInteger attempts = new AtomicInteger();
        InstrumentationAttach attach = new InstrumentationAttach(() -> {
            attempts.incrementAndGet();
            return null;
        }, property("null"), LOG);

        assertNull(attach.get());
        assertNull(attach.get());
        assertEquals(1, attempts.get());
    }

    @Test
    public void successIsCached() {
        AtomicInteger attempts = new AtomicInteger();
        Instrumentation instrumentation = fakeInstrumentation();
        InstrumentationAttach attach = new InstrumentationAttach(() -> {
            attempts.incrementAndGet();
            return instrumentation;
        }, property("success"), LOG);

        assertSame(instrumentation, attach.get());
        assertSame(instrumentation, attach.get());
        assertEquals(1, attempts.get());
    }

    @Test
    public void concurrentFirstCallsMakeOneAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch inInstaller = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Instrumentation instrumentation = fakeInstrumentation();
        InstrumentationAttach attach = new InstrumentationAttach(() -> {
            attempts.incrementAndGet();
            inInstaller.countDown();
            release.await(5L, TimeUnit.SECONDS);
            return instrumentation;
        }, property("concurrent"), LOG);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Instrumentation>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return attach.get();
                }));
            }
            start.countDown();
            assertTrue(inInstaller.await(5L, TimeUnit.SECONDS));
            Thread.sleep(100L);
            release.countDown();
            for (Future<Instrumentation> result : results) {
                assertSame(instrumentation, result.get(5L, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, attempts.get());
    }

    @Test
    public void failureIsSharedAcrossInstances() {
        String failedProperty = property("shared");
        AtomicInteger attempts = new AtomicInteger();
        Callable<Instrumentation> refused = () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("dynamic attach refused");
        };

        assertNull(new InstrumentationAttach(refused, failedProperty, LOG).get());
        assertNull(new InstrumentationAttach(refused, failedProperty, LOG).get());
        assertEquals(1, attempts.get());
    }

    private String property(String name) {
        String property = "volmlib.test.attach." + name + "." + System.nanoTime();
        usedProperties.add(property);
        return property;
    }

    private static Instrumentation fakeInstrumentation() {
        return (Instrumentation) Proxy.newProxyInstance(
                InstrumentationAttachTest.class.getClassLoader(),
                new Class<?>[] {Instrumentation.class},
                (proxy, method, args) -> null);
    }
}
