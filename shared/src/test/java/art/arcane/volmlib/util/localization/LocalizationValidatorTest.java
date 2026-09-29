package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LocalizationValidatorTest {
    @Test
    public void retainsValidEntriesAndFallsBackIndividuallyForInvalidShapesAndVariables() {
        TextKey greeting = TextKey.of("message.greeting", "Hello {name}");
        TextKey missing = TextKey.of("message.missing", "English");
        TextKey invalid = TextKey.of("message.invalid", "Value {value}");
        LinesKey lines = LinesKey.of("message.lines", "First", "Value {value}");
        LinesKey lineVariables = LinesKey.of("message.lineVariables", "Name {name}", "Value {value}");
        PluralKey plural = PluralKey.of("message.plural", "count",
                Map.of("one", "{count} block", "other", "{count} blocks"));
        TextKey wrongShape = TextKey.of("message.shape", "Text");
        TextKey optional = TextKey.ofOptional("message.optional", "{prefix}Done", "prefix");
        MessageCatalog catalog = MessageCatalog.of("en_US", greeting, missing, invalid,
                lines, lineVariables, plural, wrongShape, optional);
        LocaleOverlay candidate = LocaleOverlay.builder("French file", "fr_FR")
                .text(greeting.id(), "Bonjour {name}")
                .text(invalid.id(), "Valeur {wrong}")
                .lines(lines.id(), "Valeur {value}")
                .lines(lineVariables.id(), "Valeur {value}", "Nom {name}")
                .plural(plural.id(), Map.of("other", "{count} blocs"))
                .lines(wrongShape.id(), "Texte")
                .text(optional.id(), "Terminé")
                .text("unknown", "Inconnu")
                .build();

        LocaleOverlay accepted = LocalizationValidator.validValues(catalog, candidate);
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                catalog, List.of(accepted), PluralSelector.oneOther()));

        assertEquals("French file", accepted.source());
        assertEquals("fr_FR", accepted.locale());
        assertEquals(2, accepted.values().size());
        assertEquals(new TextValue("Bonjour {name}"), snapshot.value(greeting));
        assertEquals(new TextValue("Terminé"), snapshot.value(optional));
        for (MessageKey key : List.of(missing, invalid, lines, lineVariables, plural, wrongShape)) {
            assertEquals(key.englishValue(), snapshot.value(key));
            assertEquals("en_US", snapshot.sourceLocale(key));
        }
        assertFalse(LocalizationValidator.validate(catalog, List.of(candidate)).isValid());
    }
}
