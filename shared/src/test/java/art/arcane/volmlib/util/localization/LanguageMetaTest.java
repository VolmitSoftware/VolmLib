package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanguageMetaTest {
    @Test
    public void parsesNameAndBaseFromTheMetaTable() throws IOException {
        LanguageMeta meta = LanguageMeta.parse("greeting = \"Ahoy\"\n\n[meta]\nname = \" Pirate Speak \"\nbase = \"de_DE\"\n");

        assertEquals(Optional.of("Pirate Speak"), meta.name());
        assertEquals(Optional.of("de_DE"), meta.base());
        assertTrue(meta.hasBase());
        assertFalse(meta.isEmpty());
    }

    @Test
    public void missingTableAndWrongShapesYieldEmptyValues() throws IOException {
        assertTrue(LanguageMeta.parse("greeting = \"Ahoy\"\n").isEmpty());
        assertTrue(LanguageMeta.parse("meta = \"text\"\n").isEmpty());

        LanguageMeta meta = LanguageMeta.parse("[meta]\nname = 4\nbase = \"../en_US\"\n");

        assertTrue(meta.name().isEmpty());
        assertTrue(meta.base().isEmpty());
    }

    @Test
    public void rejectsOverlongControlAndSelfReferencingValues() {
        LanguageMeta overlong = LanguageMeta.of("x".repeat(LanguageMeta.MAXIMUM_NAME_LENGTH + 1), "en_US");
        LanguageMeta control = LanguageMeta.of("bad\u0007name", "en_US");
        LanguageMeta self = LanguageMeta.of("Austrian", "de-at").withoutSelfBase("de_AT");

        assertTrue(overlong.name().isEmpty());
        assertTrue(control.name().isEmpty());
        assertEquals(Optional.of("Austrian"), self.name());
        assertTrue(self.base().isEmpty());
    }

    @Test(expected = IOException.class)
    public void unreadableTomlFails() throws IOException {
        LanguageMeta.parse("[meta\nname = \"x\"");
    }

    @Test
    public void jsonRoundTripsThroughTheReferenceRenderer() throws IOException {
        MessageCatalog catalog = MessageCatalog.of("en_US", TextKey.of("greeting", "Hello"));

        String rendered = LanguageReferenceRenderer.render(catalog, java.util.List.of("Header"),
                LanguageMeta.of("Pirate", "en_US"));
        LanguageMeta parsed = LanguageMeta.parse(rendered);

        assertTrue(rendered.startsWith("# Header\n"));
        assertEquals(Optional.of("Pirate"), parsed.name());
        assertEquals(Optional.of("en_US"), parsed.base());
        assertEquals("Hello", TomlLanguageParser.parseText(rendered).get("greeting"));
    }
}
