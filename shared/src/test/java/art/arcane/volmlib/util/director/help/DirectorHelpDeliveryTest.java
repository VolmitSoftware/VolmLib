package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.plugin.ComponentText;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class DirectorHelpDeliveryTest {
    private static final DirectorMiniMenu.Theme THEME = new DirectorMiniMenu.Theme(
            "#E9A06B", "#F6D58A", "#533422", "#BD7C4F", "#E7DDD0", "#FF9090", "#72D6C5", "#BEB6A9");

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
    public void bukkitSpigotHelpKeepsTheAdvertisedLanguageClickAndHover() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());
        String line = "<hover:show_text:'Select your language'><click:run_command:'/siphon language self'>"
                + "<gradient:#E9A06B:#F6D58A>Your language</gradient></click></hover>";

        DirectorMiniMenu.deliver(sender, List.of(line));

        ArgumentCaptor<BaseComponent[]> components = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot, times(2)).sendMessage(components.capture());
        assertEquals("\n".repeat(DirectorMiniMenu.MENU_LINE_COUNT), BaseComponent.toPlainText(components.getAllValues().get(0)));
        assertEquals("Your language", BaseComponent.toPlainText(components.getValue()));
        String json = ComponentSerializer.toString(components.getValue());
        assertTrue(json.contains("\"action\":\"run_command\""));
        assertTrue(json.contains("\"value\":\"/siphon language self\""));
        assertTrue(json.contains("Select your language"));
        assertTrue(json.toLowerCase(Locale.ROOT).contains("#e9a06b"));
    }

    @Test
    public void generatedContentMenuKeepsNestedHexColorsAndLanguageActionsOnSpigot() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;
        Player.Spigot spigot = mock(Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());

        DirectorMiniMenu.deliverContent(sender, languageMenu(), THEME, DirectorTextResolver.ENGLISH);

        ArgumentCaptor<BaseComponent[]> components = ArgumentCaptor.forClass(BaseComponent[].class);
        verify(spigot, atLeastOnce()).sendMessage(components.capture());
        String json = components.getAllValues().stream().map(ComponentSerializer::toString)
                .filter(line -> line.contains("Your language")).findFirst().orElseThrow();
        assertTrue(json, json.contains("\"value\":\"/siphon language self\""));
        assertTrue(json, json.contains("\"action\":\"run_command\""));
        assertTrue(json, json.contains("\"action\":\"show_text\""));
        assertTrue(json, json.contains("Choose a personal language"));
        assertTrue(json, json.toLowerCase(Locale.ROOT).contains("#e9a06b"));
    }

    @Test
    public void generatedContentMenuReachesANonPublicSpigotImplementation() {
        CommandSender sender = mock(CommandSender.class, withSettings().extraInterfaces(Player.class));
        Player player = (Player) sender;
        List<BaseComponent[]> delivered = new ArrayList<>();
        Player.Spigot spigot = new Player.Spigot() {
            @Override
            public void sendMessage(BaseComponent... components) {
                delivered.add(components);
            }
        };
        when(player.spigot()).thenReturn(spigot);
        doThrow(new UnsupportedOperationException("unsupported")).when(sender).sendRichMessage(anyString());

        DirectorMiniMenu.deliverContent(sender, languageMenu(), THEME, DirectorTextResolver.ENGLISH);

        assertTrue("The Spigot implementation did not receive component messages", !delivered.isEmpty());
        assertTrue(delivered.stream().map(ComponentSerializer::toString)
                .anyMatch(json -> json.contains("\"value\":\"/siphon language self\"")));
    }

    private DirectorMiniMenu.ContentMenu languageMenu() {
        ComponentText content = ComponentText.markup("<gradient:#E9A06B:#F6D58A>Your language</gradient>"
                        + "<#BEB6A9> - </#BEB6A9><#E7DDD0>Current: en_US</#E7DDD0>")
                .clickRunCommand("/siphon language self")
                .hover(ComponentText.markup("<#F6D58A>Your language</#F6D58A><reset>\n"
                        + "<#E7DDD0>Choose a personal language</#E7DDD0>"));
        String entry = ComponentText.markup("<#BEB6A9>⇀</#BEB6A9> ").append(content).miniMessage();
        return new DirectorMiniMenu.ContentMenu("/siphon language", "/siphon language", "/siphon",
                List.of(entry), "", 1, 1);
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
