package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectorKeyedValueCompletionTest {
    @Test
    public void stringPlayerParameterCompletesOnlineNamesAfterEquals() {
        DirectorRuntimeEngine engine = engine();

        List<String> emptyValue = engine.tabComplete(
                new DirectorInvocation(new CapturingSender(), "test", List.of("glow", "player=")));
        List<String> prefix = engine.tabComplete(
                new DirectorInvocation(new CapturingSender(), "test", List.of("glow", "player=Al")));

        assertEquals(List.of("player=Alice", "player=Bob"), emptyValue);
        assertEquals(List.of("player=Alice"), prefix);
        assertFalse(emptyValue.contains("player="));
    }

    @Test
    public void stringPlayerStarDefaultIsOfferedWithOnlineNames() {
        DirectorRuntimeEngine engine = engine();

        List<String> suggestions = engine.tabComplete(
                new DirectorInvocation(new CapturingSender(), "test", List.of("state", "player=")));

        assertTrue(suggestions.contains("player=*"));
        assertTrue(suggestions.contains("player=Alice"));
        assertTrue(suggestions.contains("player=Bob"));
    }

    @Test
    public void emptyKeyedValueDoesNotEchoTheBareKey() {
        DirectorRuntimeEngine engine = engine();

        List<String> suggestions = engine.tabComplete(
                new DirectorInvocation(new CapturingSender(), "test", List.of("echo", "text=")));

        assertEquals(List.of(), suggestions);
    }

    @Test
    public void typedKeyedValueStillKeepsTheEnteredText() {
        DirectorRuntimeEngine engine = engine();

        List<String> suggestions = engine.tabComplete(
                new DirectorInvocation(new CapturingSender(), "test", List.of("echo", "text=hello")));

        assertEquals(List.of("text=hello"), suggestions);
    }

    private static DirectorRuntimeEngine engine() {
        return DirectorEngineFactory.create(
                new TestRoot(),
                DirectorEngineOptions.builder()
                        .legacyHandlers(List.of(new FakePlayerHandler()))
                        .build());
    }

    @Director(name = "test")
    public static final class TestRoot {
        @Director
        public void glow(
                @Param(name = "player") String player,
                @Param(name = "value", defaultValue = "red") String value) {
        }

        @Director
        public void state(
                @Param(name = "key") String key,
                @Param(name = "player", defaultValue = "*") String player) {
        }

        @Director
        public void echo(@Param(name = "text") String text) {
        }
    }

    private static final class FakePlayerHandler implements DirectorParameterHandler<Object> {
        @Override
        public KList<Object> getPossibilities() {
            return new KList<>(List.of("Alice", "Bob"));
        }

        @Override
        public String toString(Object value) {
            return String.valueOf(value);
        }

        @Override
        public Object parse(String in, boolean force) throws DirectorParsingException {
            return in;
        }

        @Override
        public boolean supports(Class<?> type) {
            return type == Player.class;
        }
    }

    private static final class CapturingSender implements DirectorSender {
        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isPlayer() {
            return true;
        }

        @Override
        public void sendMessage(String message) {
        }
    }
}
