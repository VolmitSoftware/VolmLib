package art.arcane.volmlib.util.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ComponentTextTest {
    @Test
    public void markupCombinesMiniMessageLegacyAndRgbSyntax() {
        ComponentText message = ComponentText.markup("<gold>Gold</gold> &aGreen \u00a7lBold [12ABef]Hex");

        assertEquals("Gold Green Bold Hex", message.plain());
        assertTrue(message.legacy().contains("\u00a76Gold"));
        assertTrue(message.legacy().contains("\u00a7aGreen"));
        assertTrue(message.legacy().contains("\u00a7lBold"));
        assertTrue(message.legacy().contains("\u00a7x\u00a71\u00a72\u00a7a\u00a7b\u00a7e\u00a7fHex"));
    }

    @Test
    public void allRgbInputsProduceTheSameLegacyColor() {
        List<String> inputs = List.of(
                "&#12ABefHex",
                "&x12ABefHex",
                "&x&1&2&A&B&e&fHex",
                "[12ABef]Hex",
                "\u00a7x\u00a71\u00a72\u00a7A\u00a7B\u00a7e\u00a7fHex");

        for (String input : inputs) {
            assertEquals("\u00a7x\u00a71\u00a72\u00a7a\u00a7b\u00a7e\u00a7fHex", ComponentText.markup(input).legacy());
        }
    }

    @Test
    public void literalSegmentsCannotBecomeFormatting() {
        ComponentText message = ComponentText.markup("<green>Hello ")
                .append(ComponentText.literal("&c<click:run_command:'/op'>Name</click>"));

        assertEquals("Hello &c<click:run_command:'/op'>Name</click>", message.plain());
        assertTrue(message.legacy().contains("&c<click:run_command:'/op'>Name</click>"));
        assertFalse(message.legacy().contains("\u00a7cName"));
    }

    @Test
    public void escapedLegacyPrefixesRemainVisibleText() {
        ComponentText message = ComponentText.markup("\\&cNot red \\[12ABef]Not hex");

        assertEquals("&cNot red [12ABef]Not hex", message.plain());
        assertFalse(message.legacy().contains("\u00a7cNot red"));
        assertFalse(message.legacy().contains("\u00a7x"));
    }

    @Test
    public void legacyOnlyStripsMiniMessageWhileRetainingLegacyColors() {
        ComponentText message = ComponentText.legacyOnly("<gradient:red:blue>Title</gradient> &aGreen");

        assertEquals("Title Green", message.plain());
        assertFalse(message.legacy().contains("gradient"));
        assertTrue(message.legacy().contains("\u00a7aGreen"));
    }

    @Test
    public void sectionInputDoesNotInterpretAmpersandOrBracketText() {
        ComponentText message = ComponentText.section("\u00a7aSafe &cLiteral [12ABef]");

        assertEquals("Safe &cLiteral [12ABef]", message.plain());
        assertTrue(message.legacy().contains("&cLiteral [12ABef]"));
    }

    @Test
    public void malformedMarkupFallsBackToLiteralLegacyText() {
        ComponentText message = ComponentText.markup("<gradient:red>Broken [12ZZef] &x12ZZef");

        assertEquals("<gradient:red>Broken [12ZZef] &x12ZZef", message.plain());
    }

    @Test
    public void nullInputsBecomeEmptyMessages() {
        assertEquals("", ComponentText.markup(null).plain());
        assertEquals("", ComponentText.legacy(null).plain());
        assertEquals("", ComponentText.literal(null).plain());
    }

    @Test
    public void componentFactoryPreservesColorAndInteractiveEvents() {
        Component source = Component.text("Run", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/test"))
                .hoverEvent(HoverEvent.showText(Component.text("Details", NamedTextColor.YELLOW)));

        ComponentText message = ComponentText.component(source);

        assertEquals(source, MiniMessage.miniMessage().deserialize(message.miniMessage()));
        assertEquals("Run", message.plain());
    }

    @Test
    public void hoverAttachesRichTextWithoutExposingAdventureTypes() {
        ComponentText message = ComponentText.literal("Status").hover(ComponentText.literal("Details"));

        assertTrue(message.miniMessage().contains("hover:show_text"));
        assertTrue(message.miniMessage().contains("Details"));
    }

    @Test
    public void openUrlAttachesAValidatedWebLink() {
        ComponentText message = ComponentText.literal("Report")
                .clickOpenUrl(URI.create("https://mclo.gs/Ab12"));

        assertTrue(message.miniMessage().contains("click:open_url"));
        assertTrue(message.miniMessage().contains("https://mclo.gs/Ab12"));
        assertThrows(IllegalArgumentException.class,
                () -> ComponentText.literal("Unsafe").clickOpenUrl(URI.create("file:///tmp/report")));
    }

    @Test
    public void componentFactoryRejectsNonComponents() {
        assertThrows(IllegalArgumentException.class, () -> ComponentText.component("not a component"));
        assertThrows(IllegalArgumentException.class, () -> ComponentText.component(null));
    }
}
