package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PluginLanguagePreferenceStoreTest {
    private static final TextKey TEXT = TextKey.of("test.text", "English");
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US", TEXT);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void injectedStoreOwnsPreferenceLoadingAndSaving() throws Exception {
        UUID existing = UUID.randomUUID();
        UUID selected = UUID.randomUUID();
        RecordingStore store = new RecordingStore(Map.of(existing, "fr_FR"));
        AtomicReference<String> defaultLocale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("en_US"));
        try (PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(
                store,
                () -> List.of("en_US", "fr_FR", "de_DE"),
                defaultLocale::get,
                active::get,
                PluginLanguagePreferenceStoreTest::snapshot,
                (locale, prepared) -> {
                    defaultLocale.set(locale);
                    active.set(prepared);
                },
                Logger.getLogger("language-preference-store-test")
        ))) {
            assertEquals("fr_FR", service.playerLocale(existing).orElseThrow());
            service.selectPlayer(selected, "de_DE").get(2L, TimeUnit.SECONDS);
            assertEquals("fr_FR", store.saved().get(existing));
            assertEquals("de_DE", store.saved().get(selected));
        }
    }

    @Test
    public void pathOptionsUseThePropertiesStoreByDefault() {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        PluginLanguageService.Options options = new PluginLanguageService.Options(
                file,
                () -> List.of("en_US"),
                () -> "en_US",
                () -> snapshot("en_US"),
                PluginLanguagePreferenceStoreTest::snapshot,
                (locale, prepared) -> {
                },
                Logger.getLogger("language-preference-default-test")
        );

        assertTrue(options.preferenceStore() instanceof PropertiesLanguagePreferenceStore);
    }

    private static LocalizationSnapshot snapshot(String locale) {
        return LocalizationSnapshot.create(new LocalizationCandidate(
                CATALOG,
                List.of(LocaleOverlay.builder(locale).text(TEXT.id(), locale).build()),
                PluralSelector.oneOther()
        ));
    }

    private static final class RecordingStore implements LanguagePreferenceStore {
        private final Map<UUID, String> loaded;
        private Map<UUID, String> saved = Map.of();

        private RecordingStore(Map<UUID, String> loaded) {
            this.loaded = Map.copyOf(loaded);
        }

        @Override
        public Map<UUID, String> load() {
            return loaded;
        }

        @Override
        public void save(Map<UUID, String> preferences) throws IOException {
            saved = Map.copyOf(new HashMap<>(preferences));
        }

        private Map<UUID, String> saved() {
            return saved;
        }
    }
}
