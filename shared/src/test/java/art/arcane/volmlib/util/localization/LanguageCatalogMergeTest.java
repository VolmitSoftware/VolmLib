package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LanguageCatalogMergeTest {
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US",
            TextKey.of("greeting", "Hello {name}"),
            TextKey.of("farewell", "Goodbye"),
            TextKey.of("menu.title", "Menu"),
            PluralKey.of("items", "count", Map.of("one", "{count} item", "other", "{count} items")),
            LinesKey.of("help.lines", "First", "Second"));

    @Test
    public void mergedContentKeepsEveryValueExactlyAcrossEscapingAndNesting() throws IOException {
        MessageCatalog catalog = MessageCatalog.of("en_US",
                TextKey.of("escaped.backslash", "C:\\languages\\en_US"),
                TextKey.of("escaped.doubled", "two \\\\ backslashes"),
                TextKey.of("escaped.quote", "say \"hello\""),
                TextKey.of("escaped.control", "tab\tline\nfeed\rreturn\bback\fform"),
                TextKey.of("escaped.unicode", "§6Größe 日本語 שלום \uD83D\uDE00"),
                TextKey.of("escaped.percent", "100% &7{name}"),
                TextKey.of("command.help.web", "Editor"),
                TextKey.of("command.help.web.open", "Open"),
                LinesKey.of("menu.lines", "First \"line\"", "Second\\line"),
                PluralKey.of("status.players", "count",
                        Map.of("one", "{count} \"player\"", "other", "{count} players\\")));
        LinkedHashMap<String, MessageValue> english = new LinkedHashMap<>();
        for (MessageKey key : catalog.keys()) {
            english.put(key.id(), key.englishValue());
        }
        String next = TomlLanguageWriter.render(english, List.of());

        String merged = LanguageCatalogMerge.merge(catalog, "", null, next).content();
        String preserved = LanguageCatalogMerge.merge(catalog, merged, next, next).content();

        assertEquals(english, TomlLanguageParser.parseValidValues(merged, catalog));
        assertEquals(english, TomlLanguageParser.parseValidValues(preserved, catalog));
    }

    @Test
    public void addsUpdatesAndKeepsManualEditsAgainstThePreviousUpstream() throws IOException {
        String previous = "# Header v1\n\ngreeting = \"Bonjour {name}\"\nfarewell = \"Au revoir\"\n";
        String local = "# Header v1\n\ngreeting = \"Salut {name}\"\nfarewell = \"Au revoir\"\n";
        String next = "# Header v2\n\ngreeting = \"Bonjour, {name}\"\nfarewell = \"Adieu\"\n\n[menu]\ntitle = \"Carte\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, previous, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());

        assertTrue(result.changed());
        assertEquals("Salut {name}", values.get("greeting"));
        assertEquals("Adieu", values.get("farewell"));
        assertEquals("Carte", values.get("menu.title"));
        assertEquals(List.of("menu.title"), result.added());
        assertEquals(List.of("farewell"), result.updated());
        assertEquals(List.of("greeting"), result.kept());
        assertTrue(result.removed().isEmpty());
        assertTrue(result.content().startsWith("# Header v2\n"));
        assertEquals("1 added, 1 updated, 0 removed, 1 kept local", result.summary());
    }

    @Test
    public void withoutAPreviousUpstreamLocalValuesAreKeptAndMissingKeysAdded() throws IOException {
        String local = "greeting = \"Salut {name}\"\n";
        String next = "greeting = \"Bonjour {name}\"\nfarewell = \"Adieu\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, null, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());

        assertEquals("Salut {name}", values.get("greeting"));
        assertEquals("Adieu", values.get("farewell"));
        assertEquals(List.of("greeting"), result.kept());
        assertEquals(List.of("farewell"), result.added());
        assertTrue(result.updated().isEmpty());
    }

    @Test
    public void removesRetiredKeysOnlyWhenTheyStillMatchThePreviousUpstream() throws IOException {
        String previous = "greeting = \"Bonjour {name}\"\n\n[old]\nuntouched = \"A\"\nedited = \"B\"\n";
        String local = "greeting = \"Bonjour {name}\"\n\n[old]\nuntouched = \"A\"\nedited = \"B!\"\n";
        String next = "greeting = \"Bonjour {name}\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, previous, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());

        assertNull(values.get("old.untouched"));
        assertEquals("B!", values.get("old.edited"));
        assertEquals(List.of("old.untouched"), result.removed());
        assertTrue(result.changed());
    }

    @Test
    public void ignoresUpstreamEntriesThatAreInvalidForThisBuild() throws IOException {
        String previous = "greeting = \"Bonjour {name}\"\nfarewell = \"Au revoir\"\n";
        String local = previous;
        String next = "greeting = \"Bonjour {nmae}\"\nfarewell = 7\n\n[menu]\ntitle = \"Carte\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, previous, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());

        assertEquals("Bonjour {name}", values.get("greeting"));
        assertEquals("Au revoir", values.get("farewell"));
        assertEquals("Carte", values.get("menu.title"));
        assertEquals(List.of("menu.title"), result.added());
        assertTrue(result.updated().isEmpty());
    }

    @Test
    public void preservesMetaAndOperatorKeysOutsideTheCatalog() throws IOException {
        String previous = "greeting = \"Ahoy {name}\"\n\n[meta]\nname = \"Pirate\"\nbase = \"en_US\"\n";
        String local = "greeting = \"Ahoy {name}\"\n\n[meta]\nname = \"Pirate\"\nbase = \"en_US\"\n\n[custom]\nextra = \"kept\"\n";
        String next = "greeting = \"Ahoy there {name}\"\nfarewell = \"Fare thee well\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, previous, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());
        LanguageMeta meta = LanguageMeta.parse(result.content());

        assertEquals("Pirate", meta.name().orElseThrow());
        assertEquals("en_US", meta.base().orElseThrow());
        assertEquals("kept", values.get("custom.extra"));
        assertEquals("Ahoy there {name}", values.get("greeting"));
        assertEquals("Fare thee well", values.get("farewell"));
    }

    @Test
    public void mergesPluralTablesAndLineListsAsWholeValues() throws IOException {
        String previous = "[items]\none = \"{count} objet\"\nother = \"{count} objets\"\n\n[help]\nlines = [\"Un\", \"Deux\"]\n";
        String local = previous;
        String next = "[items]\none = \"{count} article\"\nother = \"{count} articles\"\n\n[help]\nlines = [\"Uno\", \"Dos\"]\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, previous, next);
        Map<String, MessageValue> values = TomlLanguageParser.parseValidValues(result.content(), CATALOG);

        assertEquals(Map.of("one", "{count} article", "other", "{count} articles"),
                ((PluralValue) values.get("items")).forms());
        assertEquals(List.of("Uno", "Dos"), ((LinesValue) values.get("help.lines")).lines());
        assertEquals(java.util.Set.of("items.one", "items.other", "help.lines"), java.util.Set.copyOf(result.updated()));
    }

    @Test
    public void lineListsWithTheWrongCountKeepTheLocalValue() throws IOException {
        String previous = "[help]\nlines = [\"Un\", \"Deux\"]\n";
        String next = "[help]\nlines = [\"Un\", \"Deux\", \"Trois\"]\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, previous, previous, next);
        Map<String, MessageValue> values = TomlLanguageParser.parseValidValues(result.content(), CATALOG);

        assertEquals(List.of("Un", "Deux"), ((LinesValue) values.get("help.lines")).lines());
        assertFalse(result.changed());
    }

    @Test
    public void unchangedContentIsReportedAsNoOpEvenWithDifferentLineEndings() throws IOException {
        String next = "# Header\n\ngreeting = \"Bonjour {name}\"\nfarewell = \"Adieu\"\n";
        String local = next.replace("\n", "\r\n");

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, next, next);

        assertFalse(result.changed());
        assertEquals(0, result.changes());
        assertTrue(result.kept().isEmpty());
    }

    @Test
    public void keepsTheLocalHeaderWhenUpstreamHasNone() throws IOException {
        String local = "# Operator notes\n\ngreeting = \"Bonjour {name}\"\n";
        String next = "greeting = \"Bonjour {name}\"\nfarewell = \"Adieu\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, local, local, next);

        assertTrue(result.content().startsWith("# Operator notes\n"));
    }

    @Test
    public void createsAFileFromUpstreamAloneWhenNothingIsInstalled() throws IOException {
        String next = "# Header\n\ngreeting = \"Bonjour {name}\"\nunknown = \"dropped\"\n";

        LanguageCatalogMerge.MergeResult result = LanguageCatalogMerge.merge(CATALOG, null, null, next);
        Map<String, String> values = TomlLanguageParser.parseText(result.content());

        assertEquals("Bonjour {name}", values.get("greeting"));
        assertNull(values.get("unknown"));
        assertEquals(List.of("greeting"), result.added());
        assertTrue(result.changed());
    }

    @Test(expected = IOException.class)
    public void unreadableLocalTomlFails() throws IOException {
        LanguageCatalogMerge.merge(CATALOG, "[broken", null, "greeting = \"x\"\n");
    }
}
