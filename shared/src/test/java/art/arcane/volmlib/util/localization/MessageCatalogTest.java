package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MessageCatalogTest {
    @Test
    public void keepsCodeOwnedEnglishDefinitionsImmutable() {
        List<String> mutableLines = new ArrayList<>(List.of("First", "Second {value}"));
        Map<String, String> mutableForms = new LinkedHashMap<>();
        mutableForms.put("one", "{count} block");
        mutableForms.put("other", "{count} blocks");

        TextKey title = TextKey.of("menu.title", "Skills");
        LinesKey lore = LinesKey.of("menu.lore", mutableLines);
        PluralKey blocks = PluralKey.of("menu.blocks", "count", mutableForms);
        MessageCatalog catalog = MessageCatalog.of("en_US", title, lore, blocks);

        mutableLines.set(0, "Changed");
        mutableForms.put("other", "Changed");

        assertEquals(List.of("First", "Second {value}"), lore.english());
        assertEquals("{count} blocks", blocks.english().get("other"));
        assertEquals(List.of(title, lore, blocks), catalog.keys());
        assertThrows(UnsupportedOperationException.class, () -> catalog.byId().put("other", title));
        assertThrows(UnsupportedOperationException.class, () -> lore.english().add("Third"));
        assertThrows(UnsupportedOperationException.class, () -> blocks.english().put("few", "Few"));
    }

    @Test
    public void rejectsDuplicateCatalogKeysAndReportsShapeAndPlaceholderDrift() {
        MessageCatalog.Builder builder = MessageCatalog.builder("en_US")
                .add(TextKey.of("menu.title", "Hello {player}"))
                .add(LinesKey.of("menu.title", "Hello {viewer}"));

        LocalizationValidationException exception = assertThrows(
                LocalizationValidationException.class,
                builder::build
        );

        assertTrue(hasCode(exception.result(), LocalizationIssueCode.DUPLICATE_KEY));
        assertTrue(hasCode(exception.result(), LocalizationIssueCode.SHAPE_MISMATCH));
        assertTrue(hasCode(exception.result(), LocalizationIssueCode.PLACEHOLDER_MISMATCH));
    }

    @Test
    public void validatesPlaceholderSyntaxAndArgumentTrustKinds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TextKey.of("menu.title", "Hello {bad placeholder}")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> TextKey.of("menu.title", "Hello {player")
        );

        MessageArgs arguments = MessageArgs.builder()
                .trusted("prefix", "<green>")
                .untrusted("player", "<click:run_command:'/op me'>Player")
                .build();

        assertEquals(MessageArgumentKind.TRUSTED, arguments.require("prefix").kind());
        assertEquals(MessageArgumentKind.UNTRUSTED, arguments.require("player").kind());
        assertFalse(arguments.isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> arguments.arguments().put("other", MessageArgument.untrusted("other", "value"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageArgs.builder().untrusted("player", "one").trusted("player", "two")
        );
    }

    @Test
    public void rejectsDuplicateLocaleOverlayKeys() {
        LocaleOverlay.Builder builder = LocaleOverlay.builder("translations", "en_US")
                .text("menu.title", "First")
                .text("menu.title", "Second");

        LocalizationValidationException exception = assertThrows(
                LocalizationValidationException.class,
                builder::build
        );

        assertTrue(hasCode(exception.result(), LocalizationIssueCode.DUPLICATE_KEY));
    }

    @Test
    public void acceptsEscapedLiteralBracesWithoutDeclaringArguments() {
        TextKey key = TextKey.of("menu.literal", "Use {{name}} here");

        assertTrue(key.placeholders().isEmpty());
    }

    @Test
    public void rejectsLineCountAndPluralCategoryDrift() {
        LinesKey lore = LinesKey.of("menu.lore", "First", "Second");
        PluralKey blocks = PluralKey.of(
                "menu.blocks",
                "count",
                Map.of("one", "{count} block", "other", "{count} blocks")
        );
        MessageCatalog catalog = MessageCatalog.of("en_US", lore, blocks);
        LocaleOverlay overlay = LocaleOverlay.builder("translations", "fr_FR")
                .lines("menu.lore", "Une ligne")
                .plural("menu.blocks", Map.of("few", "Quelques blocs", "other", "{count} blocs"))
                .build();

        LocalizationValidationResult result = LocalizationValidator.validate(catalog, List.of(overlay));

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, LocalizationIssueCode.SHAPE_MISMATCH, "menu.lore"));
        assertTrue(hasIssue(result, LocalizationIssueCode.SHAPE_MISMATCH, "menu.blocks"));
    }

    @Test
    public void rejectsPlaceholderMovementBetweenLinesAndPluralForms() {
        LinesKey lore = LinesKey.of("menu.lore", "First {first}", "Second {second}");
        PluralKey blocks = PluralKey.of(
                "menu.blocks",
                "count",
                Map.of("one", "{count} block for {player}", "other", "{count} blocks")
        );
        MessageCatalog catalog = MessageCatalog.of("en_US", lore, blocks);
        LocaleOverlay overlay = LocaleOverlay.builder("translations", "fr_FR")
                .lines("menu.lore", "Premier {second}", "Deuxième {first}")
                .plural(
                        "menu.blocks",
                        Map.of("one", "{count} bloc", "other", "{count} blocs pour {player}")
                )
                .build();

        LocalizationValidationResult result = LocalizationValidator.validate(catalog, List.of(overlay));

        assertFalse(result.isValid());
        assertTrue(hasIssue(result, LocalizationIssueCode.PLACEHOLDER_MISMATCH, "menu.lore[0]"));
        assertTrue(hasIssue(result, LocalizationIssueCode.PLACEHOLDER_MISMATCH, "menu.lore[1]"));
        assertTrue(hasIssue(result, LocalizationIssueCode.PLACEHOLDER_MISMATCH, "menu.blocks.one"));
        assertTrue(hasIssue(result, LocalizationIssueCode.PLACEHOLDER_MISMATCH, "menu.blocks.other"));
    }

    private boolean hasCode(LocalizationValidationResult result, LocalizationIssueCode code) {
        for (LocalizationIssue issue : result.issues()) {
            if (issue.code() == code) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIssue(LocalizationValidationResult result, LocalizationIssueCode code, String key) {
        for (LocalizationIssue issue : result.issues()) {
            if (issue.code() == code && issue.key().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
