package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.parse.DirectorConfidence;
import art.arcane.volmlib.util.director.parse.DirectorParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DirectorUuidParameterTest {
    @Test
    public void uuidArgumentsBindPositionallyAndByNameWithOptionalFilters() {
        Commands commands = new Commands();
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(commands);
        Sender sender = new Sender();
        UUID id = UUID.randomUUID();
        assertTrue(engine.execute(new DirectorInvocation(sender, "test", List.of("history", id.toString()))).isSuccess());
        assertEquals(id, commands.island);
        assertEquals("all", commands.category);
        assertTrue(engine.execute(new DirectorInvocation(sender, "test",
                List.of("history", "island=" + id.toString().toUpperCase(Locale.ROOT), "category=settings"))).isSuccess());
        assertEquals(id, commands.island);
        assertEquals("settings", commands.category);
        assertTrue(engine.execute(new DirectorInvocation(sender, "test", List.of("default"))).isSuccess());
        assertEquals(new UUID(0, 1), commands.island);
        assertTrue(sender.messages.isEmpty());
    }

    @Test
    public void malformedAndAbbreviatedUuidsDoNotInvokeCommands() {
        Commands commands = new Commands();
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(commands);
        Sender sender = new Sender();
        for (String value : List.of("invalid", "1-1-1-1-1", "00000000-0000-0000-0000-00000000000z", "00000000000000000000000000000000")) {
            assertFalse(engine.execute(new DirectorInvocation(sender, "test", List.of("history", value))).isSuccess());
        }
        assertNull(commands.island);
        assertEquals(4, sender.messages.size());
    }

    @Test
    public void uuidParserHandlesWhitespaceAndNullInput() {
        DirectorParser<UUID> parser = DirectorEngineFactory.create(new Commands()).getParsers().get(UUID.class).orElseThrow();
        UUID id = UUID.randomUUID();
        assertEquals(id, parser.parse(" " + id + " ").getValue());
        assertEquals(DirectorConfidence.INVALID, parser.parse(null).getConfidence());
        assertEquals(DirectorConfidence.INVALID, parser.parse("").getConfidence());
    }

    @Director(name = "test")
    public static final class Commands {
        private UUID island;
        private String category;

        @Director(name = "history")
        public void history(@Param(name = "island") UUID island,
                            @Param(name = "category", defaultValue = "all") String category) {
            this.island = island;
            this.category = category;
        }

        @Director(name = "default")
        public void defaultIsland(@Param(name = "island", defaultValue = "00000000-0000-0000-0000-000000000001") UUID island) {
            this.island = island;
        }
    }

    private static final class Sender implements DirectorSender {
        private final List<String> messages = new ArrayList<>();

        @Override
        public String getName() { return "Console"; }
        @Override
        public boolean isPlayer() { return false; }
        @Override
        public void sendMessage(String message) { messages.add(message); }
    }
}
