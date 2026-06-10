package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DirectorRuntimeEngineArgumentMappingTest {
    private TestCommandRoot rootCommand;
    private DirectorRuntimeEngine engine;
    private CapturingSender sender;

    @Before
    public void setUp() {
        rootCommand = new TestCommandRoot();
        engine = DirectorEngineFactory.create(rootCommand);
        sender = new CapturingSender();
    }

    private DirectorExecutionResult run(String... args) {
        return engine.execute(new DirectorInvocation(sender, "test", List.of(args)));
    }

    @Test
    public void requiredParameterBindsPositionally() {
        DirectorExecutionResult result = run("create", "MyWorld");

        assertTrue(result.isSuccess());
        assertEquals("MyWorld", rootCommand.name);
        assertEquals("overworld", rootCommand.type);
        assertEquals(1337L, rootCommand.seed);
        assertFalse(rootCommand.main);
    }

    @Test
    public void multipleRequiredParametersBindPositionallyInOrder() {
        DirectorExecutionResult result = run("link", "from", "to");

        assertTrue(result.isSuccess());
        assertEquals("from", rootCommand.linkSource);
        assertEquals("to", rootCommand.linkTarget);
    }

    @Test
    public void optionalParametersBindWhenKeyed() {
        DirectorExecutionResult result = run("create", "MyWorld", "seed=69420", "main=true", "type=flat");

        assertEquals(List.of(), sender.messages);
        assertTrue(result.isSuccess());
        assertEquals("MyWorld", rootCommand.name);
        assertEquals("flat", rootCommand.type);
        assertEquals(69420L, rootCommand.seed);
        assertTrue(rootCommand.main);
    }

    @Test
    public void requiredParameterAcceptsKeyedForm() {
        DirectorExecutionResult result = run("create", "name=MyWorld", "seed=5");

        assertTrue(result.isSuccess());
        assertEquals("MyWorld", rootCommand.name);
        assertEquals(5L, rootCommand.seed);
    }

    @Test
    public void optionalParameterRejectsPositionalValue() {
        DirectorExecutionResult result = run("create", "MyWorld", "flat");

        assertFalse(result.isSuccess());
        assertNull(rootCommand.name);
    }

    @Test
    public void barePositionalFloodFails() {
        DirectorExecutionResult result = run("create", "69420", "true", "true", "true", "overworld");

        assertFalse(result.isSuccess());
        assertNull(rootCommand.name);
        assertFalse(rootCommand.main);
    }

    @Test
    public void unknownParameterKeyFails() {
        DirectorExecutionResult result = run("create", "MyWorld", "zzzqqq=5");

        assertFalse(result.isSuccess());
        assertNull(rootCommand.name);
    }

    @Test
    public void failureReportsUsageToSender() {
        run("create", "MyWorld", "flat");

        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("flat")));
        assertTrue(sender.messages.stream().anyMatch(message -> message.toLowerCase().contains("usage")));
    }

    @Test
    public void tabCompleteOnlySuggestsBareValuesForRequiredParameters() {
        List<String> suggestions = engine.tabComplete(new DirectorInvocation(sender, "test", List.of("create", "MyWorld", "")));

        assertFalse(suggestions.contains("true"));
        assertFalse(suggestions.contains("false"));
        assertTrue(suggestions.contains("main="));
        assertTrue(suggestions.contains("seed="));
    }

    @Director(name = "test", description = "Test root")
    public static class TestCommandRoot {
        String name;
        String type;
        long seed;
        boolean main;
        String linkSource;
        String linkTarget;

        @Director(description = "Create a world")
        public void create(
                @Param(name = "name", description = "World name")
                String name,
                @Param(name = "type", description = "Pack type", defaultValue = "overworld")
                String type,
                @Param(name = "seed", description = "World seed", defaultValue = "1337")
                long seed,
                @Param(name = "main", description = "Main world", defaultValue = "false")
                boolean main
        ) {
            this.name = name;
            this.type = type;
            this.seed = seed;
            this.main = main;
        }

        @Director(description = "Link two things")
        public void link(
                @Param(name = "source", description = "Source")
                String source,
                @Param(name = "target", description = "Target")
                String target
        ) {
            this.linkSource = source;
            this.linkTarget = target;
        }
    }

    private static final class CapturingSender implements DirectorSender {
        private final List<String> messages = new ArrayList<>();

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }
    }
}
