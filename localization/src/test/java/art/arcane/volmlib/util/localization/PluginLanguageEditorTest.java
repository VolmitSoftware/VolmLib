package art.arcane.volmlib.util.localization;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PluginLanguageEditorTest {
    private static final TextKey TEXT = TextKey.of("test.text", "Hello {name}");
    private static final LinesKey LINES = LinesKey.of("test.lines", "First {name}", "Second");
    private static final PluralKey PLURAL = PluralKey.of("test.plural", "count",
            Map.of("one", "One {name}", "other", "{count} {name}"));
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US", TEXT, LINES, PLURAL);
    private static final LocalizationSnapshot ENGLISH = LocalizationSnapshot.create(
            LocalizationCandidate.english(CATALOG, PluralSelector.oneOther()));

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final AtomicReference<String> defaultLocale = new AtomicReference<>("en_US");
    private final AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
    private PluginLanguageService languages;

    @Before
    public void setUp() {
        languages = languageService(PluginLanguageEditorTest::fullSnapshot);
    }

    private PluginLanguageService languageService(PluginLanguageService.SnapshotLoader loader) {
        return new PluginLanguageService(new PluginLanguageService.Options(
                temporaryFolder.getRoot().toPath().resolve("preferences.properties"),
                () -> List.of("en_US", "fr_FR", "de_DE"), defaultLocale::get, active::get,
                loader, (locale, snapshot) -> {
                    defaultLocale.set(locale);
                    active.set(snapshot);
                }, Logger.getLogger("PluginLanguageEditorTest")));
    }

    @After
    public void tearDown() {
        languages.close();
    }

    @Test
    public void loadingCanonicalizesWithoutSelectingAndAllowsIncompleteLocales() throws Exception {
        AtomicReference<String> requested = new AtomicReference<>();
        try (PluginLanguageEditor editor = editor(locale -> {
            requested.set(locale);
            return ENGLISH;
        }, edit -> {
            throw new AssertionError("Loading must not write");
        })) {
            PluginLanguageEditor.Document document = editor.load(" fr-fr ").get(2, TimeUnit.SECONDS);
            assertEquals("fr_FR", requested.get());
            assertEquals("fr_FR", document.locale());
            assertSame(ENGLISH, document.snapshot());
            assertEquals("en_US", languages.defaultLocale());
            assertSame(ENGLISH, languages.snapshot());
            assertFalse(temporaryFolder.getRoot().toPath().resolve("preferences.properties").toFile().exists());
        }
    }

    @Test
    public void unavailableLocaleFailsBeforeLoading() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        try (PluginLanguageEditor editor = editor(locale -> {
            loads.incrementAndGet();
            return ENGLISH;
        }, edit -> ENGLISH)) {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> editor.load("../outside").get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalArgumentException);
            assertEquals(0, loads.get());
        }
    }

    @Test
    public void preparationFailureDoesNotFallBackOrSelectEnglish() throws Exception {
        languages.selectDefault("de_DE").get(2, TimeUnit.SECONDS);
        AtomicInteger loads = new AtomicInteger();
        try (PluginLanguageEditor editor = editor(locale -> {
            loads.incrementAndGet();
            throw new IOException("Incomplete download");
        }, edit -> ENGLISH)) {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> editor.load("fr_FR").get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals(1, loads.get());
            assertEquals("de_DE", languages.defaultLocale());
        }
    }

    @Test
    public void linkageFailuresCompleteTheFutureInsteadOfLeavingItPending() throws Exception {
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot, edit -> {
            throw new IllegalAccessError("Dependency method is inaccessible");
        })) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> editor.save(
                    new PluginLanguageEditor.Edit("fr_FR", TEXT.id(), TEXT.englishValue(),
                            new TextValue("Bonjour {name}"))).get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalAccessError);
            assertEquals("en_US", editor.load("en_US").get(2, TimeUnit.SECONDS).locale());
        }
    }

    @Test
    public void savingRefreshesPersonalSnapshotsWithoutChangingSelections() throws Exception {
        UUID frenchPlayer = UUID.randomUUID();
        UUID germanPlayer = UUID.randomUUID();
        languages.selectPlayer(frenchPlayer, "fr_FR").get(2, TimeUnit.SECONDS);
        languages.selectPlayer(germanPlayer, "de_DE").get(2, TimeUnit.SECONDS);
        AtomicReference<PluginLanguageEditor.Edit> written = new AtomicReference<>();
        TextValue replacement = new TextValue("Bonjour {name}");
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot, edit -> {
            written.set(edit);
            return snapshot(edit.locale(), edit.key(), edit.value());
        })) {
            PluginLanguageEditor.Document document = editor.save(new PluginLanguageEditor.Edit(
                    "fr-fr", TEXT.id(), TEXT.englishValue(), replacement)).get(2, TimeUnit.SECONDS);
            assertEquals("fr_FR", written.get().locale());
            assertEquals(replacement, document.snapshot().value(TEXT));
            assertEquals(replacement, languages.snapshot(frenchPlayer).value(TEXT));
            assertEquals(TEXT.englishValue(), languages.snapshot(germanPlayer).value(TEXT));
            assertEquals("fr_FR", languages.playerLocale(frenchPlayer).orElseThrow());
            assertEquals("de_DE", languages.playerLocale(germanPlayer).orElseThrow());
            assertEquals("en_US", languages.defaultLocale());
            assertSame(ENGLISH, languages.snapshot());
        }
    }

    @Test
    public void saveRejectsAStaleExpectedValueBeforeWriting() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        try (PluginLanguageEditor editor = editor(locale -> snapshot(locale, TEXT.id(),
                new TextValue("Changed externally {name}")), edit -> {
            writes.incrementAndGet();
            return ENGLISH;
        })) {
            ExecutionException failure = assertThrows(ExecutionException.class, () -> editor.save(
                    new PluginLanguageEditor.Edit("fr_FR", TEXT.id(), TEXT.englishValue(),
                            new TextValue("New {name}"))).get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals(0, writes.get());
        }
    }

    @Test
    public void saveValidatesShapesAndEveryLineAndPluralPlaceholder() throws Exception {
        AtomicInteger writes = new AtomicInteger();
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot, edit -> {
            writes.incrementAndGet();
            return ENGLISH;
        })) {
            List<PluginLanguageEditor.Edit> invalid = List.of(
                    new PluginLanguageEditor.Edit("fr_FR", TEXT.id(), TEXT.englishValue(),
                            new LinesValue(List.of("Wrong {name}"))),
                    new PluginLanguageEditor.Edit("fr_FR", TEXT.id(), TEXT.englishValue(),
                            new TextValue("Missing")),
                    new PluginLanguageEditor.Edit("fr_FR", LINES.id(), LINES.englishValue(),
                            new LinesValue(List.of("First", "Second {name}"))),
                    new PluginLanguageEditor.Edit("fr_FR", PLURAL.id(), PLURAL.englishValue(),
                            new PluralValue(Map.of("one", "One", "other", "{count} {name}"))),
                    new PluginLanguageEditor.Edit("fr_FR", "unknown", TEXT.englishValue(),
                            new TextValue("Hello {name}")));
            for (PluginLanguageEditor.Edit edit : invalid) {
                assertThrows(ExecutionException.class, () -> editor.save(edit).get(2, TimeUnit.SECONDS));
            }
            assertEquals(0, writes.get());
        }
    }

    @Test
    public void saveSupportsListsAndPluralForms() throws Exception {
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot,
                edit -> snapshot(edit.locale(), edit.key(), edit.value()))) {
            LinesValue lines = new LinesValue(List.of("Premier {name}", "Deuxieme"));
            PluralValue plural = new PluralValue(Map.of("one", "Un {name}", "other", "{count} pour {name}"));
            assertEquals(lines, editor.save(new PluginLanguageEditor.Edit("fr_FR", LINES.id(),
                    LINES.englishValue(), lines)).get(2, TimeUnit.SECONDS).snapshot().value(LINES));
            assertEquals(plural, editor.save(new PluginLanguageEditor.Edit("fr_FR", PLURAL.id(),
                    PLURAL.englishValue(), plural)).get(2, TimeUnit.SECONDS).snapshot().value(PLURAL));
        }
    }

    @Test
    public void writerFailureDoesNotReplaceThePersonalCache() throws Exception {
        UUID player = UUID.randomUUID();
        languages.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
        LocalizationSnapshot previous = languages.snapshot(player);
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot, edit -> {
            throw new IOException("Disk full");
        })) {
            assertThrows(ExecutionException.class, () -> editor.save(new PluginLanguageEditor.Edit(
                    "fr_FR", TEXT.id(), TEXT.englishValue(), new TextValue("Bonjour {name}")))
                    .get(2, TimeUnit.SECONDS));
            assertSame(previous, languages.snapshot(player));
        }
    }

    @Test
    public void cancellationDuringPreparationPreventsTheWrite() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger();
        try (PluginLanguageEditor editor = editor(locale -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return ENGLISH;
        }, edit -> {
            writes.incrementAndGet();
            return ENGLISH;
        })) {
            CompletableFuture<PluginLanguageEditor.Document> result = editor.save(new PluginLanguageEditor.Edit(
                    "fr_FR", TEXT.id(), TEXT.englishValue(), new TextValue("Bonjour {name}")));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertTrue(result.cancel(true));
            release.countDown();
            editor.load("en_US").get(2, TimeUnit.SECONDS);
            assertThrows(CancellationException.class, result::join);
            assertEquals(0, writes.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void cancellationAfterACommittedWriteStillPublishesThePersonalSnapshot() throws Exception {
        UUID player = UUID.randomUUID();
        languages.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
        CountDownLatch committed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TextValue replacement = new TextValue("Bonjour {name}");
        LocalizationSnapshot saved = snapshot("fr_FR", TEXT.id(), replacement);
        try (PluginLanguageEditor editor = editor(PluginLanguageEditorTest::fullSnapshot, edit -> {
            committed.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return saved;
        })) {
            CompletableFuture<PluginLanguageEditor.Document> result = editor.save(new PluginLanguageEditor.Edit(
                    "fr_FR", TEXT.id(), TEXT.englishValue(), replacement));
            assertTrue(committed.await(2, TimeUnit.SECONDS));
            assertTrue(result.cancel(true));
            release.countDown();
            editor.load("en_US").get(2, TimeUnit.SECONDS);
            assertSame(saved, languages.snapshot(player));
            assertEquals("fr_FR", languages.playerLocale(player).orElseThrow());
            assertTrue(result.isCancelled());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void closedLanguageServiceDoesNotAcceptANewCachedSnapshot() throws Exception {
        UUID player = UUID.randomUUID();
        languages.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
        languages.close();
        languages.cache("fr_FR", fullSnapshot("fr_FR"));
        assertSame(ENGLISH, languages.snapshot(player));
    }

    @Test
    public void closingCancelsRunningAndQueuedWorkAndRejectsNewRequests() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (PluginLanguageEditor editor = editor(locale -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return ENGLISH;
        }, edit -> ENGLISH)) {
            CompletableFuture<PluginLanguageEditor.Document> running = editor.load("fr_FR");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<PluginLanguageEditor.Document> queued = editor.load("de_DE");
            editor.close();
            assertTrue(running.isCancelled());
            assertTrue(queued.isCancelled());
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> editor.load("en_US").get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            languages.selectDefault("en_US").get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    public void editsWaitForInFlightSelectionsBeforePublishing() throws Exception {
        languages.close();
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writing = new CountDownLatch(1);
        AtomicReference<LocalizationSnapshot> source = new AtomicReference<>(fullSnapshot("fr_FR"));
        languages = languageService(locale -> {
            LocalizationSnapshot loaded = source.get();
            prepared.countDown();
            release.await(2, TimeUnit.SECONDS);
            return loaded;
        });
        TextValue replacement = new TextValue("Bonjour {name}");
        LocalizationSnapshot updated = snapshot("fr_FR", TEXT.id(), replacement);
        try (PluginLanguageEditor editor = editor(locale -> source.get(), edit -> {
            writing.countDown();
            source.set(updated);
            if (languages.defaultLocale().equals(edit.locale())) {
                active.set(updated);
            }
            return updated;
        })) {
            CompletableFuture<Void> selection = languages.selectDefault("fr_FR");
            assertTrue(prepared.await(2, TimeUnit.SECONDS));
            CompletableFuture<PluginLanguageEditor.Document> saved = editor.save(new PluginLanguageEditor.Edit(
                    "fr_FR", TEXT.id(), TEXT.englishValue(), replacement));
            assertFalse(writing.await(100, TimeUnit.MILLISECONDS));
            release.countDown();
            selection.get(2, TimeUnit.SECONDS);
            saved.get(2, TimeUnit.SECONDS);
            assertSame(updated, languages.snapshot());
            assertEquals("fr_FR", languages.defaultLocale());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void selectionsQueuedBehindAnEditReadTheSavedSnapshot() throws Exception {
        languages.close();
        AtomicReference<LocalizationSnapshot> source = new AtomicReference<>(fullSnapshot("fr_FR"));
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch selecting = new CountDownLatch(1);
        languages = languageService(locale -> {
            selecting.countDown();
            return source.get();
        });
        TextValue replacement = new TextValue("Bonjour {name}");
        LocalizationSnapshot updated = snapshot("fr_FR", TEXT.id(), replacement);
        try (PluginLanguageEditor editor = editor(locale -> source.get(), edit -> {
            writing.countDown();
            release.await(2, TimeUnit.SECONDS);
            source.set(updated);
            return updated;
        })) {
            CompletableFuture<PluginLanguageEditor.Document> saved = editor.save(new PluginLanguageEditor.Edit(
                    "fr_FR", TEXT.id(), TEXT.englishValue(), replacement));
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> selection = languages.selectDefault("fr_FR");
            assertFalse(selecting.await(100, TimeUnit.MILLISECONDS));
            release.countDown();
            saved.get(2, TimeUnit.SECONDS);
            selection.get(2, TimeUnit.SECONDS);
            assertSame(updated, languages.snapshot());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void closingTheLanguageServiceCompletesQueuedEditorRequests() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (PluginLanguageEditor editor = editor(locale -> {
            entered.countDown();
            release.await(2, TimeUnit.SECONDS);
            return ENGLISH;
        }, edit -> ENGLISH)) {
            CompletableFuture<PluginLanguageEditor.Document> running = editor.load("fr_FR");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<PluginLanguageEditor.Document> queued = editor.load("de_DE");
            languages.close();
            assertThrows(ExecutionException.class, () -> running.get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> queued.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    private PluginLanguageEditor editor(PluginLanguageEditor.SnapshotLoader loader,
                                        PluginLanguageEditor.MessageWriter writer) {
        return new PluginLanguageEditor(languages, new PluginLanguageEditor.Options(loader, writer));
    }

    private static LocalizationSnapshot fullSnapshot(String locale) {
        return snapshot(locale, TEXT.id(), TEXT.englishValue());
    }

    private static LocalizationSnapshot snapshot(String locale, String key, MessageValue replacement) {
        LocaleOverlay.Builder overlay = LocaleOverlay.builder(locale);
        for (MessageKey definition : CATALOG.keys()) {
            overlay.put(definition.id(), definition.id().equals(key) ? replacement : definition.englishValue());
        }
        return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                List.of(overlay.build()), PluralSelector.oneOther()));
    }
}
