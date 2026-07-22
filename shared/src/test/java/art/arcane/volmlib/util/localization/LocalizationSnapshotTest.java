package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class LocalizationSnapshotTest {
    private static final TextKey GREETING = TextKey.of(
            "message.greeting",
            "{prefix}Hello {player}"
    );
    private static final LinesKey LORE = LinesKey.of(
            "message.lore",
            "First line",
            "Value: {value}"
    );
    private static final PluralKey BLOCKS = PluralKey.of(
            "message.blocks",
            "count",
            Map.of("one", "{count} block", "other", "{count} blocks")
    );
    private static final TextKey ENGLISH_ONLY = TextKey.of(
            "message.english-only",
            "English default"
    );
    private static final MessageCatalog CATALOG = MessageCatalog.of(
            "en_US",
            GREETING,
            LORE,
            BLOCKS,
            ENGLISH_ONLY
    );

    @Test
    public void resolvesPrimaryFallbackAndEnglishLayersInPriorityOrder() {
        LocaleOverlay primary = LocaleOverlay.builder("primary", "fr_FR")
                .text("message.greeting", "{prefix}Bonjour {player}")
                .build();
        LocaleOverlay fallback = LocaleOverlay.builder("fallback", "de_DE")
                .text("message.greeting", "{prefix}Hallo {player}")
                .lines("message.lore", "Erste Zeile", "Wert: {value}")
                .plural("message.blocks", Map.of(
                        "one", "{count} Block",
                        "other", "{count} Blöcke"
                ))
                .build();
        AtomicReference<String> selectedLocale = new AtomicReference<>();
        PluralSelector selector = (locale, quantity) -> {
            selectedLocale.set(locale);
            return quantity.intValue() == 1 ? "one" : "other";
        };
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                CATALOG,
                List.of(primary, fallback),
                selector
        ));

        MessageArgs greetingArguments = MessageArgs.builder()
                .trusted("prefix", "<green>")
                .untrusted("player", "Alex")
                .build();
        ResolvedText greeting = snapshot.resolve(GREETING, greetingArguments);
        ResolvedLines lore = snapshot.resolve(
                LORE,
                MessageArgs.builder().untrusted("value", 12).build()
        );
        ResolvedText blocks = snapshot.resolve(
                BLOCKS,
                MessageArgs.builder().untrusted("count", 2).build()
        );
        ResolvedText english = snapshot.resolve(ENGLISH_ONLY);

        assertEquals("{prefix}Bonjour {player}", greeting.template());
        assertEquals("fr_FR", greeting.locale());
        assertEquals(MessageArgumentKind.UNTRUSTED, greeting.arguments().require("player").kind());
        assertEquals(List.of("Erste Zeile", "Wert: {value}"), lore.lines());
        assertEquals("de_DE", lore.locale());
        assertEquals("{count} Blöcke", blocks.template());
        assertEquals("de_DE", selectedLocale.get());
        assertEquals("English default", english.template());
        assertEquals("en_US", english.locale());
        assertTrue(snapshot.validation().isValid());
        assertFalse(snapshot.validation().warnings().isEmpty());
    }

    @Test
    public void rejectsUnknownShapeAndPlaceholderDriftWhileReportingMissingValues() {
        LocaleOverlay invalid = LocaleOverlay.builder("broken", "fr_FR")
                .text("message.greeting", "Missing placeholders")
                .text("message.lore", "Wrong shape")
                .plural("message.blocks", Map.of("other", "No count"))
                .text("message.unknown", "Unused")
                .build();

        LocalizationValidationResult result = LocalizationValidator.validate(CATALOG, List.of(invalid));

        assertFalse(result.isValid());
        assertTrue(hasCode(result, LocalizationIssueCode.SHAPE_MISMATCH));
        assertTrue(hasCode(result, LocalizationIssueCode.PLACEHOLDER_MISMATCH));
        assertTrue(hasCode(result, LocalizationIssueCode.UNUSED_KEY));
        assertTrue(hasCode(result, LocalizationIssueCode.MISSING_KEY));
        assertThrows(
                LocalizationValidationException.class,
                () -> LocalizationSnapshot.create(new LocalizationCandidate(
                        CATALOG,
                        List.of(invalid),
                        PluralSelector.oneOther()
                ))
        );
    }

    @Test
    public void requiresExactNamedArgumentsAndNumericPluralSelectorValues() {
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(
                LocalizationCandidate.english(CATALOG, PluralSelector.oneOther())
        );

        assertThrows(IllegalArgumentException.class, () -> snapshot.resolve(
                GREETING,
                MessageArgs.builder().untrusted("player", "Alex").build()
        ));
        assertThrows(IllegalArgumentException.class, () -> snapshot.resolve(
                GREETING,
                MessageArgs.builder()
                        .untrusted("prefix", "")
                        .untrusted("player", "Alex")
                        .untrusted("extra", "value")
                        .build()
        ));
        assertThrows(IllegalArgumentException.class, () -> snapshot.resolve(
                BLOCKS,
                MessageArgs.builder().untrusted("count", "two").build()
        ));
    }

    @Test
    public void pluralSelectionFallsBackToOtherWhenTheLocaleOmitsTheSelectedCategory() {
        LocaleOverlay overlay = LocaleOverlay.builder("ar")
                .plural("message.blocks", Map.of("one", "One", "other", "Other {count}"))
                .build();
        LocalizationSnapshot snapshot = LocalizationSnapshot.create(new LocalizationCandidate(
                CATALOG,
                List.of(overlay),
                (locale, quantity) -> "few"
        ));

        ResolvedText resolved = snapshot.resolve(
                BLOCKS,
                MessageArgs.builder().untrusted("count", 3).build()
        );

        assertEquals("Other {count}", resolved.template());
    }

    private boolean hasCode(LocalizationValidationResult result, LocalizationIssueCode code) {
        for (LocalizationIssue issue : result.issues()) {
            if (issue.code() == code) {
                return true;
            }
        }
        return false;
    }
}
