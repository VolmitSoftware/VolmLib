package art.arcane.volmlib.util.bukkit.papi;

import org.bukkit.OfflinePlayer;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VolmitPlaceholderExpansionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Test
    public void onRequest_blankParamsReturnNull() {
        Recorder recorder = new Recorder();
        VolmitPlaceholderExpansion expansion = expansion(workingRegistry(), recorder);

        assertNull(expansion.onRequest(player(PLAYER), null));
        assertNull(expansion.onRequest(player(PLAYER), ""));
        assertNull(expansion.onRequest(player(PLAYER), " "));
        assertNull(expansion.onRequest(player(PLAYER), "\t"));
        assertTrue(recorder.records.isEmpty());
    }

    @Test
    public void onRequest_unknownPathReturnsNullSoTheLiteralIsVisible() {
        VolmitPlaceholderExpansion expansion = expansion(workingRegistry(), new Recorder());

        assertNull(expansion.onRequest(player(PLAYER), "player.levl"));
        assertNull(expansion.onRequest(player(PLAYER), "nonsense"));
    }

    @Test
    public void onRequest_lowercasesTheWholeParamsStringOnce() {
        VolmitPlaceholderExpansion expansion = expansion(workingRegistry(), new Recorder());

        assertEquals("7", expansion.onRequest(player(PLAYER), "Player.Level"));
        assertEquals("7", expansion.onRequest(player(PLAYER), "PLAYER.LEVEL"));
    }

    @Test
    public void onRequest_touchesNothingOnThePlayerBeyondGetUniqueId() {
        VolmitPlaceholderExpansion expansion = expansion(PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("player.id", playerId -> String.valueOf(playerId))
                .build(), new Recorder());

        assertEquals(PLAYER.toString(), expansion.onRequest(player(PLAYER), "player.id"));
    }

    @Test
    public void onRequest_withNoPlayerStillServesServerScopedKeys() {
        VolmitPlaceholderExpansion expansion = expansion(PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("tps", playerId -> "20.00")
                .key("player.level", playerId -> playerId == null ? PlaceholderValues.UNAVAILABLE : "7")
                .build(), new Recorder());

        assertEquals("20.00", expansion.onRequest(null, "tps"));
        assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(null, "player.level"));
    }

    @Test
    public void onRequest_isTotalAndLogsOncePerDistinctPathWhenAResolverThrows() {
        Recorder recorder = new Recorder();
        AtomicInteger calls = new AtomicInteger();
        VolmitPlaceholderExpansion expansion = expansion(PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("broken.one", playerId -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("HiddenOre API access requires the owning world region thread");
                })
                .key("broken.two", playerId -> {
                    throw new ArrayIndexOutOfBoundsException(2);
                })
                .key("broken.three", playerId -> {
                    throw new StackOverflowError();
                })
                .build(), recorder);

        for (int index = 0; index < 500; index++) {
            assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(player(PLAYER), "broken.one"));
        }

        assertEquals(500, calls.get());
        assertEquals(1, recorder.records.size());

        assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(player(PLAYER), "broken.two"));
        assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(player(PLAYER), "broken.two"));
        assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(player(PLAYER), "broken.three"));

        assertEquals(3, recorder.records.size());
        assertTrue(recorder.records.get(0).getMessage().contains("volmlibtest"));
        assertTrue(recorder.records.get(0).getMessage().contains("broken.one"));
    }

    @Test
    public void onRequest_isTotalWhenThePlayerItselfIsHostile() {
        VolmitPlaceholderExpansion expansion = expansion(workingRegistry(), new Recorder());

        assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(hostilePlayer(), "player.level"));
    }

    @Test
    public void loggingIsBoundedAcrossUnboundedDistinctPaths() {
        Recorder recorder = new Recorder();
        VolmitPlaceholderExpansion expansion = expansion(PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .group("broken", (playerId, tail) -> {
                    throw new IllegalStateException(tail);
                })
                .build(), recorder);

        for (int index = 0; index < 5000; index++) {
            assertEquals(PlaceholderValues.UNAVAILABLE, expansion.onRequest(player(PLAYER), "broken.path-" + index));
        }

        assertEquals(64, recorder.records.size());
    }

    @Test
    public void metadataIsCapturedAtConstructionAndPublishedFromTheRegistry() {
        VolmitPlaceholderExpansion expansion = expansion(workingRegistry(), new Recorder());

        assertEquals("volmlibtest", expansion.getIdentifier());
        assertEquals("Volmit Software", expansion.getAuthor());
        assertEquals("1.0.0", expansion.getVersion());
        assertEquals("VolmLibTest", expansion.getRequiredPlugin());
        assertTrue(expansion.persist());
        assertEquals(List.of("available", "player.level"), expansion.getPlaceholders());
    }

    @Test
    public void constructionRejectsIdentifiersPapiCannotDispatchAndRegistriesWithoutTheReservedKey() {
        Recorder recorder = new Recorder();
        PlaceholderKeyRegistry registry = workingRegistry();

        assertThrows(IllegalArgumentException.class, () -> new TestExpansion("volm_lib", registry, recorder.logger));
        assertThrows(IllegalArgumentException.class, () -> new TestExpansion("VolmLib", registry, recorder.logger));
        assertThrows(IllegalArgumentException.class, () -> new TestExpansion("", registry, recorder.logger));
        assertThrows(IllegalArgumentException.class, () -> new TestExpansion("volmlibtest", PlaceholderKeyRegistry.builder()
                .key("player.level", playerId -> "7")
                .build(), recorder.logger));
    }

    private static PlaceholderKeyRegistry workingRegistry() {
        return PlaceholderKeyRegistry.builder()
                .key(PlaceholderKeyRegistry.AVAILABLE, playerId -> PlaceholderValues.TRUE)
                .key("player.level", playerId -> playerId == null ? PlaceholderValues.UNAVAILABLE : "7")
                .build();
    }

    private static VolmitPlaceholderExpansion expansion(PlaceholderKeyRegistry registry, Recorder recorder) {
        return new TestExpansion("volmlibtest", registry, recorder.logger);
    }

    private static OfflinePlayer player(UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "OfflinePlayer[" + id + "]";
            default -> throw new UnsupportedOperationException("placeholder resolution touched " + method.getName());
        });
    }

    private static OfflinePlayer hostilePlayer() {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class}, (proxy, method, args) -> {
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static final class TestExpansion extends VolmitPlaceholderExpansion {
        private TestExpansion(String identifier, PlaceholderKeyRegistry registry, Logger logger) {
            super(identifier, "Volmit Software", "1.0.0", "VolmLibTest", registry, logger);
        }
    }

    private static final class Recorder extends Handler {
        private final List<LogRecord> records = new ArrayList<>();
        private final Logger logger = Logger.getAnonymousLogger();

        private Recorder() {
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(this);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
