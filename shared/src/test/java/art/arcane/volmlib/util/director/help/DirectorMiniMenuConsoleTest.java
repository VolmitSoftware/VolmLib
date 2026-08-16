package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.localization.TextKey;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectorMiniMenuConsoleTest {
    private static final String[] MENU_MARKUP = {
            "<gradient:",
            "<hover:",
            "<click:",
            "<font:",
            "<strikethrough>",
            "<reset>",
            "<#",
            "</",
            "⇀",
            "⊰",
            "⊱",
            "(((",
            ")))",
            "❭",
            "〈",
            "✎",
            "✒",
            "✦",
            "⚠",
            "✔",
            "✢"
    };

    private DirectorRuntimeEngine engine;
    private Map<String, String> translations;
    private DirectorTextResolver resolver;

    @Before
    public void setUp() {
        engine = DirectorEngineFactory.create(new TestCommands());
        translations = new HashMap<>();
        resolver = (key, arguments) -> {
            String translation = translations.get(key.id());
            if (translation == null) {
                return DirectorTextResolver.ENGLISH.resolve(key, arguments);
            }
            return DirectorTextResolver.ENGLISH.resolve(TextKey.of(key.id(), translation), arguments);
        };
    }

    private List<String> console(List<String> args, int pageSize) {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, args, pageSize).orElseThrow();
        return DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH);
    }

    @Test
    public void listsEveryChildIgnoringPagination() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 2).orElseThrow();

        assertEquals(2, page.entries().size());
        assertEquals(2, page.totalPages());
        assertEquals(
                List.of(
                        "--- /test ---",
                        "empty - Category of Commands",
                        "ping - Check connectivity",
                        "pregen - Category of Commands",
                        "silent - No description provided"
                ),
                DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH)
        );
    }

    @Test
    public void headerShowsTheFullCommandPathWithoutPageIndicator() {
        List<String> lines = console(List.of("pregen"), 1);

        assertEquals("--- /test pregen ---", lines.get(0));
        assertFalse(lines.get(0).contains("{"));
    }

    @Test
    public void formatsRequiredAndOptionalParametersAndHidesContextualOnes() {
        List<String> lines = console(List.of("pregen"), 17);

        assertEquals(
                List.of(
                        "--- /test pregen ---",
                        "start <radius=...> [world=...] [pattern=spiral] - Start a pregeneration task",
                        "stop - Stop the running pregeneration"
                ),
                lines
        );
    }

    @Test
    public void omitsMiniMessageMarkupAndMenuGlyphs() {
        String rendered = String.join("\n", console(List.of(), 2)) + "\n" + String.join("\n", console(List.of("pregen"), 1));

        for (String markup : MENU_MARKUP) {
            assertFalse(markup, rendered.contains(markup));
        }
    }

    @Test
    public void reportsCategoriesWithoutChildren() {
        assertEquals(
                List.of("--- /test empty ---", "No subcommands on this page."),
                console(List.of("empty"), 17)
        );
    }

    @Test
    public void usesResolverTranslations() {
        translations.put("test.pregen.start.description", "Pregenerierung starten");
        translations.put("director.help.category", "Kategorie von Befehlen");
        translations.put("director.help.no_description", "Keine Beschreibung vorhanden");

        DirectorMiniMenu.DirectorHelpPage root = DirectorMiniMenu.resolveHelp(engine, List.of(), 17).orElseThrow();
        DirectorMiniMenu.DirectorHelpPage pregen = DirectorMiniMenu.resolveHelp(engine, List.of("pregen"), 17).orElseThrow();

        String rendered = String.join("\n", DirectorMiniMenu.renderConsole(root, resolver))
                + "\n" + String.join("\n", DirectorMiniMenu.renderConsole(pregen, resolver));

        assertTrue(rendered.contains("pregen - Kategorie von Befehlen"));
        assertTrue(rendered.contains("silent - Keine Beschreibung vorhanden"));
        assertTrue(rendered.contains("- Pregenerierung starten"));
    }

    @Test
    public void nullResolverFallsBackToEnglish() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 17).orElseThrow();

        assertEquals(
                DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH),
                DirectorMiniMenu.renderConsole(page, null)
        );
    }

    @Test
    public void toleratesNullPage() {
        assertEquals(List.of(), DirectorMiniMenu.renderConsole(null, DirectorTextResolver.ENGLISH));
    }

    @Test
    public void deliversPlainConsoleListingToNonPlayerSenders() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 2).orElseThrow();
        ConsoleSender sender = new ConsoleSender();

        DirectorMiniMenu.deliver(sender, page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);

        assertEquals(DirectorMiniMenu.renderConsole(page, DirectorTextResolver.ENGLISH), sender.plain);
        assertTrue(sender.rich.isEmpty());
    }

    @Test
    public void deliversRichMenuToPlayerSenders() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 2).orElseThrow();
        PlayerSender sender = new PlayerSender();

        DirectorMiniMenu.deliver(sender, page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);

        List<String> expected = new ArrayList<>();
        expected.add("\n".repeat(19));
        expected.addAll(DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH));

        assertEquals(expected, sender.rich);
    }

    @Test
    public void deliverToleratesNullSenderAndNullPage() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 17).orElseThrow();

        DirectorMiniMenu.deliver(null, page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
        DirectorMiniMenu.deliver(new ConsoleSender(), null, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);
    }

    public static final class ConsoleSender {
        final List<String> rich = new ArrayList<>();
        final List<String> plain = new ArrayList<>();

        public void sendRichMessage(String message) {
            rich.add(message);
        }

        public void sendMessage(String message) {
            plain.add(message);
        }
    }

    public static final class PlayerSender implements org.bukkit.entity.Player {
        final List<String> rich = new ArrayList<>();

        public void sendRichMessage(String message) {
            rich.add(message);
        }

        public void sendMessage(String message) {
            rich.add(message);
        }
    }

    @Director(name = "test", description = "Test commands")
    public static class TestCommands {
        EmptyCommands empty;
        PregenCommands pregen;

        @Director(name = "ping", description = "Check connectivity")
        public void ping() {
        }

        @Director(name = "silent")
        public void silent() {
        }
    }

    @Director(name = "pregen", description = "Pregeneration commands")
    public static class PregenCommands {
        @Director(name = "start", description = "Start a pregeneration task", descriptionKey = "test.pregen.start.description")
        public void start(
                @Param(name = "radius", description = "Radius in blocks")
                int radius,
                @Param(name = "world", contextual = true, contextualOverride = true)
                String world,
                @Param(name = "pattern", description = "Traversal pattern", defaultValue = "spiral")
                String pattern
        ) {
        }

        @Director(name = "stop", description = "Stop the running pregeneration")
        public void stop(
                @Param(name = "sender", contextual = true)
                String sender
        ) {
        }
    }

    @Director(name = "empty", description = "Empty group")
    public static class EmptyCommands {
    }
}
