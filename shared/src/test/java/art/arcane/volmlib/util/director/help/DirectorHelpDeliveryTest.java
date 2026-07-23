package art.arcane.volmlib.util.director.help;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectorHelpDeliveryTest {

    public static final class RichSender {
        final List<String> rich = new ArrayList<>();
        final List<String> plain = new ArrayList<>();

        public void sendRichMessage(String message) {
            rich.add(message);
        }

        public void sendMessage(String message) {
            plain.add(message);
        }
    }

    public static final class LegacySender {
        final List<String> plain = new ArrayList<>();

        public void sendMessage(String message) {
            plain.add(message);
        }
    }

    public static final class FakePlayer implements org.bukkit.entity.Player {
        final List<String> rich = new ArrayList<>();

        public void sendRichMessage(String message) {
            rich.add(message);
        }

        public void sendMessage(String message) {
            rich.add(message);
        }
    }

    @Test
    public void deliversRawMiniMessageToNativeRichSink() {
        RichSender sender = new RichSender();
        String hoverLine = "<hover:show_text:'<#00BFFF>react.action</#00BFFF><reset>\n<#99c2ff>Details</#99c2ff>'>"
                + "<click:run_command:react.action help=1>✦ react.action</click></hover>";

        DirectorMiniMenu.deliver(sender, List.of(hoverLine));

        assertEquals(List.of(hoverLine), sender.rich);
        assertTrue(sender.plain.isEmpty());
    }

    @Test
    public void deliversEveryLineInOrder() {
        RichSender sender = new RichSender();
        List<String> lines = List.of("<#003366>one</#003366>", "<#00BFFF>two</#00BFFF>", "three");

        DirectorMiniMenu.deliver(sender, lines);

        assertEquals(lines, sender.rich);
    }

    @Test
    public void skipsBlankLines() {
        RichSender sender = new RichSender();

        DirectorMiniMenu.deliver(sender, List.of("", "   ", "<#003366>kept</#003366>"));

        assertEquals(List.of("<#003366>kept</#003366>"), sender.rich);
    }

    @Test
    public void fallsBackToStrippedPlainTextWhenRichSinkAbsent() {
        LegacySender sender = new LegacySender();
        String hoverLine = "<hover:show_text:'x'><#00BFFF>label</#00BFFF></hover>";

        DirectorMiniMenu.deliver(sender, List.of(hoverLine));

        assertEquals(List.of("label"), sender.plain);
    }

    @Test
    public void fallbackDoesNotLeakHoverTooltipTextWhenArgumentContainsAngleBrackets() {
        String realNodeLine = "<hover:show_text:'<#00BFFF>react.action</#00BFFF><reset>\n<#99c2ff>Details</#99c2ff>'>"
                + "<click:run_command:react.action help=1>✦ react.action</click></hover> <#7d93b2>(act, a)</#7d93b2>";

        assertEquals("✦ react.action (act, a)", DirectorMiniMenu.stripMiniMessage(realNodeLine));
    }

    @Test
    public void fallbackUnescapesLiteralAngleBracketsFromParameterText() {
        assertEquals("use <name> here", DirectorMiniMenu.stripMiniMessage("use \\<name\\> here"));
        assertEquals("value <= 10", DirectorMiniMenu.stripMiniMessage("value \\<= 10"));
    }

    @Test
    public void fallbackUnescapesEscapedBackslashesFromParameterText() {
        assertEquals("path C:\\test", DirectorMiniMenu.stripMiniMessage("path C:\\\\test"));
        assertEquals("a \\ b", DirectorMiniMenu.stripMiniMessage("a \\\\ b"));
    }

    @Test
    public void toleratesNullSenderAndNullLines() {
        DirectorMiniMenu.deliver(null, List.of("a"));
        DirectorMiniMenu.deliver(new RichSender(), null);
    }

    @Test
    public void pushesChatClearBeforeHelpLinesForPlayerSenders() {
        FakePlayer sender = new FakePlayer();

        DirectorMiniMenu.deliver(sender, List.of("<#003366>one</#003366>", "<#00BFFF>two</#00BFFF>"));

        assertEquals(3, sender.rich.size());
        assertEquals("\n".repeat(19), sender.rich.get(0));
        assertEquals("<#003366>one</#003366>", sender.rich.get(1));
        assertEquals("<#00BFFF>two</#00BFFF>", sender.rich.get(2));
    }

    @Test
    public void doesNotPushChatClearForNonPlayerSenders() {
        RichSender sender = new RichSender();

        DirectorMiniMenu.deliver(sender, List.of("<#003366>one</#003366>"));

        assertEquals(List.of("<#003366>one</#003366>"), sender.rich);
    }

    @Test
    public void doesNotPushChatClearWhenThereAreNoRenderableLines() {
        FakePlayer sender = new FakePlayer();

        DirectorMiniMenu.deliver(sender, List.of("", "   "));

        assertTrue(sender.rich.isEmpty());
    }
}
