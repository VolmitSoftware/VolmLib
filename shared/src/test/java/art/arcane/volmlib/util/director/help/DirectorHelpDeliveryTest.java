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
    public void toleratesNullSenderAndNullLines() {
        DirectorMiniMenu.deliver(null, List.of("a"));
        DirectorMiniMenu.deliver(new RichSender(), null);
    }
}
