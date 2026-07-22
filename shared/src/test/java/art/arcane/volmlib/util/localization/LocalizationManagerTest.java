package art.arcane.volmlib.util.localization;

import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LocalizationManagerTest {
    private static final TextKey TITLE = TextKey.of("menu.title", "English title");
    private static final TextKey BODY = TextKey.of("menu.body", "English body {player}");
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US", TITLE, BODY);

    @Test
    public void swapsOnlyAfterTheWholeCandidateValidates() {
        LocalizationManager manager = new LocalizationManager(LocalizationCandidate.english(
                CATALOG,
                PluralSelector.oneOther()
        ));
        LocalizationSnapshot initial = manager.snapshot();
        LocaleOverlay valid = LocaleOverlay.builder("fr_FR")
                .text("menu.title", "Titre français")
                .text("menu.body", "Corps français {player}")
                .build();

        LocalizationReloadResult applied = manager.reload(new LocalizationCandidate(
                CATALOG,
                List.of(valid),
                PluralSelector.oneOther()
        ));

        assertTrue(applied.applied());
        assertSame(initial, applied.previous());
        assertNotSame(initial, manager.snapshot());
        assertEquals("Titre français", manager.snapshot().resolve(TITLE).template());

        LocalizationSnapshot lastGood = manager.snapshot();
        LocaleOverlay partiallyInvalid = LocaleOverlay.builder("broken")
                .text("menu.title", "Would have changed")
                .lines("menu.body", "Wrong shape")
                .build();
        LocalizationReloadResult rejected = manager.reload(new LocalizationCandidate(
                CATALOG,
                List.of(partiallyInvalid),
                PluralSelector.oneOther()
        ));

        assertFalse(rejected.applied());
        assertSame(lastGood, rejected.previous());
        assertSame(lastGood, rejected.current());
        assertSame(lastGood, manager.snapshot());
        assertEquals("Titre français", manager.snapshot().resolve(TITLE).template());
        assertFalse(rejected.validation().isValid());
    }

    @Test
    public void retainsLastGoodSnapshotWhenCandidateLoadingFails() {
        LocalizationManager manager = new LocalizationManager(LocalizationCandidate.english(
                CATALOG,
                PluralSelector.oneOther()
        ));
        LocalizationSnapshot initial = manager.snapshot();

        LocalizationReloadResult result = manager.reload(() -> {
            throw new IOException("Cannot read locale source");
        });

        assertFalse(result.applied());
        assertSame(initial, result.current());
        assertSame(initial, manager.snapshot());
        assertTrue(result.failure() instanceof IOException);
    }

    @Test
    public void retainsLastGoodSnapshotWhenLoaderBuildsDuplicateDefinitions() {
        LocalizationManager manager = new LocalizationManager(LocalizationCandidate.english(
                CATALOG,
                PluralSelector.oneOther()
        ));
        LocalizationSnapshot initial = manager.snapshot();

        LocalizationReloadResult result = manager.reload(() -> {
            MessageCatalog duplicate = MessageCatalog.builder("en_US")
                    .add(TextKey.of("duplicate", "First"))
                    .add(TextKey.of("duplicate", "Second"))
                    .build();
            return LocalizationCandidate.english(duplicate, PluralSelector.oneOther());
        });

        assertFalse(result.applied());
        assertSame(initial, manager.snapshot());
        assertTrue(hasCode(result.validation(), LocalizationIssueCode.DUPLICATE_KEY));
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
