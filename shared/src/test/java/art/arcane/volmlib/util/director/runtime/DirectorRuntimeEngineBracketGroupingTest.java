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

public class DirectorRuntimeEngineBracketGroupingTest {
    private HologramCommandRoot rootCommand;
    private DirectorRuntimeEngine engine;
    private CapturingSender sender;

    @Before
    public void setUp() {
        rootCommand = new HologramCommandRoot();
        engine = DirectorEngineFactory.create(rootCommand);
        sender = new CapturingSender();
    }

    private DirectorExecutionResult run(String... args) {
        return engine.execute(new DirectorInvocation(sender, "test", List.of(args)));
    }

    private List<String> tab(String... args) {
        return engine.tabComplete(new DirectorInvocation(sender, "test", List.of(args)));
    }

    @Test
    public void multiWordBracketValueBindsAsSingleValue() {
        DirectorExecutionResult result = run("addline", "id=123", "text=[This", "is", "an", "example.]");

        assertEquals(List.of(), sender.messages);
        assertTrue(result.isSuccess());
        assertEquals("123", rootCommand.id);
        assertEquals("This is an example.", rootCommand.text);
    }

    @Test
    public void consumedGroupTokensAreNeverParsedAsKeys() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[keep", "id=9", "here]");

        assertTrue(result.isSuccess());
        assertEquals("1", rootCommand.id);
        assertEquals("keep id=9 here", rootCommand.text);
    }

    @Test
    public void midTokenCloserWithTrailingCharactersStaysLiteral() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[ff0000]Red");

        assertTrue(result.isSuccess());
        assertEquals("[ff0000]Red", rootCommand.text);
    }

    @Test
    public void singleTokenGroupOfOneStripsBrackets() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[whole]");

        assertTrue(result.isSuccess());
        assertEquals("whole", rootCommand.text);
    }

    @Test
    public void bareHexColorValueStaysLiteral() {
        assertTrue(run("addline", "id=1", "text=[ff0000]").isSuccess());
        assertEquals("[ff0000]", rootCommand.text);

        assertTrue(run("addline", "id=1", "text=[A1B2C3]").isSuccess());
        assertEquals("[A1B2C3]", rootCommand.text);

        assertTrue(run("addline", "id=1", "text=[zzzzzz]").isSuccess());
        assertEquals("zzzzzz", rootCommand.text);
    }

    @Test
    public void unclosedGroupFailsNamingKeyAndMissingBracket() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[This", "is");

        assertFalse(result.isSuccess());
        assertNull(rootCommand.text);
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("text") && message.contains("]")));
    }

    @Test
    public void positionalBracketTokensAreUntouched() {
        DirectorExecutionResult result = run("addline", "123", "[banana]");

        assertTrue(result.isSuccess());
        assertEquals("123", rootCommand.id);
        assertEquals("[banana]", rootCommand.text);
    }

    @Test
    public void doubledBracketsEscapeToLiteralBrackets() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[[literal", "brackets]]");

        assertTrue(result.isSuccess());
        assertEquals("[literal brackets]", rootCommand.text);
    }

    @Test
    public void multipleGroupsBindIndependentlyInOneCommand() {
        DirectorExecutionResult result = run("banner", "top=[first", "line]", "bottom=[second", "line]");

        assertTrue(result.isSuccess());
        assertEquals("first line", rootCommand.top);
        assertEquals("second line", rootCommand.bottom);
    }

    @Test
    public void groupFollowedByMoreKeyedPairsBindsBoth() {
        DirectorExecutionResult result = run("addline", "text=[a", "b]", "id=7");

        assertTrue(result.isSuccess());
        assertEquals("7", rootCommand.id);
        assertEquals("a b", rootCommand.text);
    }

    @Test
    public void emptyGroupBindsEmptyString() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[]");

        assertTrue(result.isSuccess());
        assertEquals("", rootCommand.text);
    }

    @Test
    public void quotedValueContainingBracketsIsNeverGrouped() {
        DirectorExecutionResult result = run("addline", "id=1", "\"text=[a b]\"");

        assertTrue(result.isSuccess());
        assertEquals("[a b]", rootCommand.text);
    }

    @Test
    public void pretokenizedValueContainingBracketsIsNeverGrouped() {
        DirectorExecutionResult result = engine.execute(DirectorInvocation.pretokenized(
                sender, "test", List.of("addline", "id=1", "text=[ff0000]Hello [player]")));

        assertTrue(result.isSuccess());
        assertEquals("[ff0000]Hello [player]", rootCommand.text);
    }

    @Test
    public void hexColorInsideGroupSurvivesGrouping() {
        DirectorExecutionResult result = run("addline", "id=1", "text=[Hello", "[ff0000]world", "here]");

        assertTrue(result.isSuccess());
        assertEquals("Hello [ff0000]world here", rootCommand.text);
    }

    @Test
    public void tabCompletionInsideOpenGroupSuggestsNothingAndDoesNotThrow() {
        assertEquals(List.of(), tab("addline", "id=1", "text=[This", "is", "an"));
        assertEquals(List.of(), tab("addline", "text=[This", "is", ""));
        assertEquals(List.of(), tab("addline", "text=["));
    }

    @Test
    public void tabCompletionAfterClosedGroupMarksParameterConsumed() {
        List<String> suggestions = tab("addline", "text=[a", "b]", "");

        assertFalse(suggestions.stream().anyMatch(suggestion -> suggestion.startsWith("text=")));
        assertTrue(suggestions.stream().anyMatch(suggestion -> suggestion.startsWith("id=")));
    }

    @Director(name = "hologram")
    public static class HologramCommandRoot {
        String id;
        String text;
        String top;
        String bottom;

        @Director
        public void addline(
                @Param(name = "id") String id,
                @Param(name = "text") String text
        ) {
            this.id = id;
            this.text = text;
        }

        @Director
        public void banner(
                @Param(name = "top") String top,
                @Param(name = "bottom") String bottom
        ) {
            this.top = top;
            this.bottom = bottom;
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
