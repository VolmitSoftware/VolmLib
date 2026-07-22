package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.director.DirectorTextResolver;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DirectorMiniMenuLocalizationTest {
    private DirectorRuntimeEngine engine;
    private Map<String, String> translations;
    private List<TextRequest> requests;
    private DirectorTextResolver resolver;

    @Before
    public void setUp() {
        engine = DirectorEngineFactory.create(new TestCommands());
        translations = new HashMap<>();
        requests = new ArrayList<>();
        resolver = (key, arguments) -> {
            requests.add(new TextRequest(key.id(), key.english()));
            return translations.getOrDefault(key.id(), DirectorTextResolver.ENGLISH.resolve(key, arguments));
        };
    }

    @Test
    public void resolvesCommandParameterAndGenericHelpText() {
        translations.put("test.create.description", "Welt erstellen");
        translations.put("test.create.name.description", "Weltname");
        translations.put("test.create.type.description", "Pakettyp");
        translations.put("director.help.aliases", "Aliase");
        translations.put("director.help.command_group", "Befehlsgruppe öffnen.");
        translations.put("director.help.no_parameters", "Keine Parameter; Befehl vorfüllen.");
        translations.put("director.help.parameters", "Parameter");
        translations.put("director.help.parameter.required", "erforderlich");
        translations.put("director.help.parameter.optional", "optional");
        translations.put("director.help.parameter.default", "standard");
        translations.put("director.help.navigation.page", "Seite");

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 10).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver));

        assertTrue(rendered.contains("Welt erstellen"));
        assertTrue(rendered.contains("Weltname"));
        assertTrue(rendered.contains("Pakettyp"));
        assertTrue(rendered.contains("Aliase: make"));
        assertTrue(rendered.contains("Befehlsgruppe öffnen."));
        assertTrue(rendered.contains("Keine Parameter; Befehl vorfüllen."));
        assertTrue(rendered.contains("Parameter:"));
        assertTrue(rendered.contains("String, erforderlich"));
        assertTrue(rendered.contains("String, optional, standard=overworld"));
        assertTrue(rendered.contains("Seite 1 / 1"));
        assertTrue(requests.contains(new TextRequest("test.create.description", "Create a world")));
        assertTrue(requests.contains(new TextRequest("test.create.name.description", "World name")));
        assertTrue(requests.contains(new TextRequest("test.create.type.description", "Pack type")));
        assertTrue(requests.contains(new TextRequest("director.help.command_group", "Command group. Click to open.")));
        assertTrue(requests.contains(new TextRequest("director.help.no_parameters", "No parameters. Click to prefill command.")));
    }

    @Test
    public void resolvesParentAndEmptyPageText() {
        translations.put("director.help.navigation.parent.hover", "Zur übergeordneten Gruppe");
        translations.put("director.help.navigation.back", "Zurück");
        translations.put("director.help.no_subcommands", "Keine Unterbefehle auf dieser Seite.");
        translations.put("director.help.navigation.page", "Seite");

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("empty", "help=1"), 10).orElseThrow();
        List<String> lines = DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver);
        String rendered = String.join("\n", lines);

        assertTrue(rendered.contains("Zur übergeordneten Gruppe"));
        assertTrue(rendered.contains("〈 Zurück"));
        assertTrue(rendered.contains("Keine Unterbefehle auf dieser Seite."));
        assertTrue(rendered.contains("Seite 1 / 1"));
    }

    @Test
    public void englishResolverPreservesExistingEnglishOutput() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 10).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH));

        assertTrue(rendered.contains("Create a world"));
        assertTrue(rendered.contains("World name"));
        assertTrue(rendered.contains("Aliases: make"));
        assertTrue(rendered.contains("Command group. Click to open."));
        assertTrue(rendered.contains("No parameters. Click to prefill command."));
        assertTrue(rendered.contains("Parameters:"));
        assertTrue(rendered.contains("String, required"));
        assertTrue(rendered.contains("String, optional, default=overworld"));
        assertTrue(rendered.contains("Page 1 / 1"));
    }

    @Test
    public void resolvesPreviousAndNextPageText() {
        translations.put("director.help.navigation.previous.hover", "Vorherige Seite");
        translations.put("director.help.navigation.next.hover", "Nächste Seite");
        translations.put("director.help.navigation.page", "Seite");

        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("help=2"), 1).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver));

        assertTrue(rendered.contains("Vorherige Seite"));
        assertTrue(rendered.contains("Nächste Seite"));
        assertTrue(rendered.contains("〈 Seite 1"));
        assertTrue(rendered.contains("Seite 2 / 4"));
        assertTrue(rendered.contains("Seite 3 ❭"));
    }

    @Test
    public void descriptionsWithoutKeysRemainEnglish() {
        DirectorTextResolver rejectingResolver = (key, arguments) -> {
            if (key.id().isBlank()) {
                throw new AssertionError("Unexpected empty description key");
            }

            return DirectorTextResolver.ENGLISH.resolve(key, arguments);
        };
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of("unkeyed", "help=1"), 10).orElseThrow();
        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), rejectingResolver));

        assertTrue(rendered.contains("No key description"));
    }

    @Test
    public void omittedDescriptionUsesLocalizedGenericFallback() {
        translations.put("director.help.no_description", "Keine Beschreibung vorhanden");
        DirectorRuntimeEngine localEngine = DirectorEngineFactory.create(new OmittedDescriptionCommands());
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(localEngine, List.of(), 10).orElseThrow();

        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver));

        assertTrue(rendered.contains("Keine Beschreibung vorhanden"));
        assertFalse(rendered.contains(Director.DEFAULT_DESCRIPTION));
        assertEquals(
                2L,
                requests.stream()
                        .filter(request -> request.key().equals("director.help.no_description"))
                        .count()
        );
    }

    @Test
    public void contextualParametersRemainHiddenWithoutLocalizationRequest() {
        DirectorRuntimeEngine localEngine = DirectorEngineFactory.create(new ContextualCommands());
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(localEngine, List.of(), 10).orElseThrow();

        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver));

        assertTrue(rendered.contains("No parameters. Click to prefill command."));
        assertFalse(rendered.contains("sender"));
        assertFalse(requests.stream().anyMatch(request -> request.key().equals("director.help.parameter.contextual")));
    }

    @Test
    public void helpCatalogExactlyMatchesRenderedGenericSurface() {
        assertEquals(
                List.of(
                        "director.help.navigation.parent.hover",
                        "director.help.navigation.back",
                        "director.help.no_subcommands",
                        "director.help.no_description",
                        "director.help.aliases",
                        "director.help.command_group",
                        "director.help.no_parameters",
                        "director.help.parameters",
                        "director.help.parameter.required",
                        "director.help.parameter.optional",
                        "director.help.parameter.default",
                        "director.help.navigation.previous.hover",
                        "director.help.navigation.next.hover",
                        "director.help.navigation.page"
                ),
                DirectorHelpMessages.keys().stream().map(key -> key.id()).toList()
        );
    }

    @Test
    public void nullResolverFallsBackToEnglish() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 10).orElseThrow();

        assertEquals(
                DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH),
                DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), null)
        );
    }

    @Test
    public void resolverReturningNullFallsBackToEnglish() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 10).orElseThrow();

        assertEquals(
                DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH),
                DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), (key, arguments) -> null)
        );
    }

    @Test
    public void translatedHelpTextCannotInjectMiniMessage() {
        translations.put("director.help.navigation.parent.hover", "<click:run_command:'/op @s'>Unsafe</click>");
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(
                engine,
                List.of("empty", "help=1"),
                10
        ).orElseThrow();

        String rendered = String.join("\n", DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), resolver));

        assertTrue(rendered.contains("\\\\<click:run_command:\\'/op @s\\'\\\\>Unsafe\\\\</click\\\\>"));
        assertFalse(rendered.contains("show_text:'<click:run_command:'/op @s'>"));
    }

    @Test
    public void borderFormattingClosesNestedTagsInOrder() {
        DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(engine, List.of(), 10).orElseThrow();
        List<String> lines = DirectorMiniMenu.render(page, DirectorMiniMenu.Theme.reactBlue(), DirectorTextResolver.ENGLISH);

        assertTrue(lines.get(1).endsWith("</gradient></strikethrough></font>"));
        assertTrue(lines.get(lines.size() - 1).endsWith("</gradient></strikethrough></font>"));
    }

    @Test
    public void partialCommandMatchingDoesNotDependOnTheJvmLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            DirectorMiniMenu.DirectorHelpPage page = DirectorMiniMenu.resolveHelp(
                    engine,
                    List.of("PINGGROU", "HELP=1"),
                    10
            ).orElseThrow();

            assertEquals("empty", page.node().getDescriptor().getName());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private record TextRequest(String key, String englishDefault) {
    }

    @Director(name = "test", description = "Test commands", descriptionKey = "test.description")
    public static class TestCommands {
        EmptyCommands empty;

        @Director(name = "create", aliases = "make", description = "Create a world", descriptionKey = "test.create.description")
        public void create(
                @Param(name = "name", description = "World name", descriptionKey = "test.create.name.description")
                String name,
                @Param(name = "type", description = "Pack type", descriptionKey = "test.create.type.description", defaultValue = "overworld")
                String type
        ) {
        }

        @Director(name = "ping", description = "Check connectivity", descriptionKey = "test.ping.description")
        public void ping() {
        }

        @Director(name = "unkeyed", description = "No key description")
        public void unkeyed() {
        }
    }

    @Director(name = "empty", aliases = "pinggroup", description = "Empty group", descriptionKey = "test.empty.description")
    public static class EmptyCommands {
    }

    @Director(name = "test")
    public static class OmittedDescriptionCommands {
        @Director
        public void undocumented(@Param(name = "value") String value) {
        }
    }

    @Director(name = "test", description = "Test commands")
    public static class ContextualCommands {
        @Director(description = "Contextual command")
        public void contextual(@Param(name = "sender", contextual = true) String sender) {
        }
    }
}
