package art.arcane.volmlib.util.matter.slices;

import art.arcane.volmlib.util.matter.MatterReader;
import art.arcane.volmlib.util.matter.MatterWriter;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RawMatterRegistryTest {
    @Test
    public void absentReadersAndWritersDoNotAllocateRegistries() throws Exception {
        RegisteredMatter matter = new RegisteredMatter();
        assertNull(matter.writeInto(StringBuilder.class));
        assertNull(matter.readFrom(StringBuilder.class));
        assertNull(registry(matter, "writers"));
        assertNull(registry(matter, "readers"));
        assertThrows(NullPointerException.class, () -> matter.writeInto((Class<Object>) null));
        assertThrows(NullPointerException.class, () -> matter.readFrom((Class<Object>) null));

        MatterWriter<StringBuilder, Integer> writer = (target, value, x, y, z) -> target.append(value);
        matter.writer(StringBuilder.class, writer);
        assertSame(writer, matter.writeInto(StringBuilder.class));
        assertNull(registry(matter, "readers"));
        assertNull(new RegisteredMatter().writeInto(StringBuilder.class));

        MatterReader<StringBuilder, Integer> reader = (source, x, y, z) -> source.length();
        matter.reader(StringBuilder.class, reader);
        assertSame(reader, matter.readFrom(StringBuilder.class));
        assertNull(new RegisteredMatter().readFrom(StringBuilder.class));
        MatterWriter<StringBuilder, Integer> replacement = (target, value, x, y, z) -> target.append(x);
        matter.writer(StringBuilder.class, replacement);
        assertSame(replacement, matter.writeInto(StringBuilder.class));
    }

    @Test(timeout = 10_000L)
    public void concurrentFirstRegistrationsKeepEveryMedium() throws Exception {
        RegisteredMatter matter = new RegisteredMatter();
        List<Class<?>> types = List.of(String.class, Integer.class, Long.class, Double.class,
                Float.class, Byte.class, Short.class, Boolean.class);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(types.size());
        List<Future<?>> futures = new ArrayList<>();
        List<MatterWriter<Object, Integer>> writers = new ArrayList<>();
        List<MatterReader<Object, Integer>> readers = new ArrayList<>();
        try {
            for (Class<?> type : types) {
                MatterWriter<Object, Integer> writer = (target, value, x, y, z) -> { };
                MatterReader<Object, Integer> reader = (source, x, y, z) -> type.hashCode();
                writers.add(writer);
                readers.add(reader);
                futures.add(executor.submit(() -> {
                    start.await();
                    register(matter, type, writer, reader);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5L, TimeUnit.SECONDS);
            }
            for (int index = 0; index < types.size(); index++) {
                assertSame(writers.get(index), matter.writeInto(types.get(index)));
                assertSame(readers.get(index), matter.readFrom(types.get(index)));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @SuppressWarnings("unchecked")
    private static void register(RegisteredMatter matter, Class<?> type,
                                 MatterWriter<Object, Integer> writer, MatterReader<Object, Integer> reader) {
        matter.writer((Class<Object>) type, writer);
        matter.reader((Class<Object>) type, reader);
    }

    private static Object registry(RawMatter<?> matter, String name) throws Exception {
        Field field = RawMatter.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(matter);
    }

    private static final class RegisteredMatter extends IntMatter {
        private <W> void writer(Class<W> type, MatterWriter<W, Integer> writer) {
            registerWriter(type, writer);
        }

        private <W> void reader(Class<W> type, MatterReader<W, Integer> reader) {
            registerReader(type, reader);
        }
    }
}
