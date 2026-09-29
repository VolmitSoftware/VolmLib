package art.arcane.volmlib.util.localization;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PluginLanguageServiceTest {
    private static final TextKey TEXT = TextKey.of("test.text", "English");
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US", TEXT);
    private static final LocalizationSnapshot ENGLISH = snapshot("en_US");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void coordinatedReloadWaitsForSelectionBeforeTakingPublicationMonitor() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("coordinated.properties");
        Object publication = new Object();
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        CountDownLatch selectionEntered = new CountDownLatch(1);
        CountDownLatch releaseSelection = new CountDownLatch(1);
        CountDownLatch reloadEntered = new CountDownLatch(1);
        PluginLanguageService.Options options = new PluginLanguageService.Options(file, () -> List.of("en_US", "de_DE"),
                locale::get, active::get, PluginLanguageServiceTest::snapshot, (selected, prepared) -> {
                    selectionEntered.countDown();
                    assertTrue(releaseSelection.await(2, TimeUnit.SECONDS));
                    synchronized (publication) {
                        locale.set(selected);
                        active.set(prepared);
                    }
                }, Logger.getLogger("CoordinatedLanguageTest"));
        try (PluginLanguageService service = new PluginLanguageService(options)) {
            CompletableFuture<Void> selection = service.selectDefault("de_DE");
            assertTrue(selectionEntered.await(2, TimeUnit.SECONDS));
            CompletableFuture<Void> reload = CompletableFuture.runAsync(() -> {
                reloadEntered.countDown();
                try {
                    service.commitUpdate(() -> {
                        synchronized (publication) {
                            locale.set("en_US");
                            active.set(ENGLISH);
                            service.invalidate();
                            service.cache("en_US", ENGLISH);
                        }
                        return null;
                    });
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            });
            assertTrue(reloadEntered.await(2, TimeUnit.SECONDS));
            releaseSelection.countDown();
            selection.get(2, TimeUnit.SECONDS);
            reload.get(2, TimeUnit.SECONDS);
            assertEquals("en_US", service.defaultLocale());
            assertSame(ENGLISH, service.snapshot());
        } finally {
            releaseSelection.countDown();
        }
    }

    @Test
    public void closedServiceRejectsCoordinatedPublication() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("closed-update.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        PluginLanguageService service = service(file, locale, active, PluginLanguageServiceTest::snapshot);
        service.close();
        assertThrows(IllegalStateException.class, () -> service.commitUpdate(() -> {
            locale.set("de_DE");
            return null;
        }));
        assertEquals("en_US", locale.get());
    }

    @Test
    public void playerOverridesRemainIndependentOfServerAndOtherPlayers() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, PluginLanguageServiceTest::snapshot)) {
            service.selectPlayer(first, "fr_fr").get(2, TimeUnit.SECONDS);
            assertEquals("fr_FR", service.snapshot(first).resolve(TEXT).template());
            assertSame(ENGLISH, service.snapshot(second));
            assertSame(ENGLISH, service.snapshot());
            service.selectDefault("de_DE").get(2, TimeUnit.SECONDS);
            assertEquals("fr_FR", service.snapshot(first).resolve(TEXT).template());
            assertEquals("de_DE", service.snapshot(second).resolve(TEXT).template());
            service.clearPlayer(first).get(2, TimeUnit.SECONDS);
            assertEquals("de_DE", service.snapshot(first).resolve(TEXT).template());
            assertFalse(service.playerLocale(first).isPresent());
        }
    }

    @Test
    public void preferencesSurviveRestartAndLoadWithoutBlockingRendering() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        UUID player = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, PluginLanguageServiceTest::snapshot)) {
            service.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
        }
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            return snapshot(requested);
        })) {
            assertEquals("fr_FR", service.playerLocale(player).orElseThrow());
            assertSame(ENGLISH, service.snapshot(player));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertSame(ENGLISH, service.snapshot(player));
            release.countDown();
            service.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
            assertEquals("fr_FR", service.snapshot(player).resolve(TEXT).template());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void failedPreparationSelectsAndPersistsValidatedEnglish() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("fr_FR"));
        UUID player = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            if (requested.equals("de_DE")) {
                throw new IOException("Download failed");
            }
            return snapshot(requested);
        })) {
            service.selectPlayer(player, "fr_FR").get(2, TimeUnit.SECONDS);
            service.selectPlayer(player, "de_DE").get(2, TimeUnit.SECONDS);
            assertEquals("en_US", service.effectiveLocale(player));
            assertEquals("en_US", service.snapshot(player).resolve(TEXT).template());
            assertEquals(player + "=en_US\n", Files.readString(file));
            assertEquals("fr_FR", service.defaultLocale());
            service.selectDefault("de_DE").get(2, TimeUnit.SECONDS);
            assertEquals("en_US", active.get().resolve(TEXT).template());
            assertEquals("en_US", locale.get());
        }
    }

    @Test
    public void englishFallbackLogsTheOriginalDownloadFailureAndCauseOnce() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("failed-download.properties");
        IOException failure = new IOException("Download failed", new IllegalArgumentException("Invalid response"));
        AtomicReference<LogRecord> warning = new AtomicReference<>();
        AtomicInteger records = new AtomicInteger();
        AtomicReference<String> locale = new AtomicReference<>("de_DE");
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warning.set(record);
                records.incrementAndGet();
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        PluginLanguageService.Options options = new PluginLanguageService.Options(file,
                () -> List.of("en_US", "de_DE"), locale::get, () -> ENGLISH, requested -> {
                    if (requested.equals("de_DE")) {
                        throw failure;
                    }
                    return ENGLISH;
                }, (selected, prepared) -> locale.set(selected), logger);

        try (PluginLanguageService service = new PluginLanguageService(options)) {
            service.selectDefault("de_DE").get(2L, TimeUnit.SECONDS);
            assertEquals("en_US", service.defaultLocale());
            assertEquals(1, records.get());
            assertEquals(Level.WARNING, warning.get().getLevel());
            assertTrue(warning.get().getMessage().contains("de_DE"));
            assertTrue(warning.get().getMessage().contains("en_US"));
            assertSame(failure, warning.get().getThrown());
            assertSame(failure.getCause(), warning.get().getThrown().getCause());
        }
    }

    @Test
    public void partialPreparationRetainsServerAndPlayerSelectionsWithEnglishForMissingEntries() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("fr_FR"));
        UUID player = UUID.randomUUID();
        TextKey translated = TextKey.of("test.translated", "Saved");
        MessageCatalog partialCatalog = MessageCatalog.of("en_US", TEXT, translated);
        LocalizationSnapshot incomplete = LocalizationSnapshot.create(new LocalizationCandidate(partialCatalog,
                List.of(LocaleOverlay.builder("de_DE").text(translated.id(), "Gespeichert").build()),
                PluralSelector.oneOther()));
        try (PluginLanguageService service = service(file, locale, active, requested ->
                requested.equals("de_DE") ? incomplete : snapshot(requested))) {
            service.selectDefault("de_DE").get(2, TimeUnit.SECONDS);
            service.selectPlayer(player, "de_DE").get(2, TimeUnit.SECONDS);
            assertEquals("de_DE", service.defaultLocale());
            assertEquals("de_DE", service.effectiveLocale(player));
            assertEquals("en_US", active.get().sourceLocale(TEXT));
            assertEquals("en_US", service.snapshot(player).sourceLocale(TEXT));
            assertEquals("Gespeichert", active.get().resolve(translated).template());
            assertEquals("Gespeichert", service.snapshot(player).resolve(translated).template());
            assertEquals(player + "=de_DE\n", Files.readString(file));
        }
    }

    @Test
    public void sparseOverrideAboveACompleteLocaleRemainsSelectable() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        LocalizationSnapshot german = LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                List.of(LocaleOverlay.builder("overrides", "de_DE").build(),
                        LocaleOverlay.builder("downloaded", "de_DE").text(TEXT.id(), "Deutsch").build()),
                PluralSelector.oneOther()));
        try (PluginLanguageService service = service(file, locale, active, requested ->
                requested.equals("de_DE") ? german : snapshot(requested))) {
            service.selectDefault("de_DE").get(2, TimeUnit.SECONDS);
            assertEquals("de_DE", service.defaultLocale());
            assertEquals("Deutsch", service.snapshot().resolve(TEXT).template());
        }
    }

    @Test
    public void failedEnglishOverrideUsesValidatedBuiltInEnglish() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("fr_FR"));
        AtomicInteger loads = new AtomicInteger();
        UUID player = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            loads.incrementAndGet();
            throw new IOException("Invalid locale contents");
        })) {
            service.selectPlayer(player, "de_DE").get(2, TimeUnit.SECONDS);
            assertEquals(2, loads.get());
            assertEquals("en_US", service.effectiveLocale(player));
            assertEquals("English", service.snapshot(player).resolve(TEXT).template());
            assertTrue(service.snapshot(player).validation().isValid());
            service.selectDefault("en_US").get(2, TimeUnit.SECONDS);
            assertEquals(3, loads.get());
            assertEquals("en_US", service.defaultLocale());
            assertEquals("English", active.get().resolve(TEXT).template());
        }
    }

    @Test
    public void persistedFailedPersonalLocalesAreReplacedWithEnglish() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Files.writeString(file, first + "=de_DE\n" + second + "=de_DE\n");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("fr_FR"));
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            if (requested.equals("de_DE")) {
                throw new IOException("Download failed");
            }
            return snapshot(requested);
        })) {
            service.snapshot(first);
            service.selectDefault("fr_FR").get(2, TimeUnit.SECONDS);
            assertEquals("en_US", service.playerLocale(first).orElseThrow());
            assertEquals("en_US", service.playerLocale(second).orElseThrow());
            assertEquals("en_US", service.snapshot(first).sourceLocale(TEXT));
            assertEquals("fr_FR", service.defaultLocale());
            assertFalse(Files.readString(file).contains("de_DE"));
        }
    }

    @Test
    public void cancelledPreparationDoesNotSelectEnglish() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        LocalizationSnapshot french = snapshot("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(french);
        AtomicInteger loads = new AtomicInteger();
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            loads.incrementAndGet();
            throw new CancellationException("Cancelled");
        })) {
            assertThrows(CancellationException.class, () -> service.selectDefault("de_DE").get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
            assertEquals("fr_FR", service.defaultLocale());
            assertSame(french, active.get());
            assertFalse(Files.exists(file));
        }
    }

    @Test
    public void failedPersonalWriteDoesNotFallbackOrChangeSelection() throws Exception {
        Path file = temporaryFolder.newFolder("preferences.properties").toPath();
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(snapshot("fr_FR"));
        AtomicInteger loads = new AtomicInteger();
        UUID player = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            loads.incrementAndGet();
            return snapshot(requested);
        })) {
            assertThrows(ExecutionException.class, () -> service.selectPlayer(player, "de_DE").get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
            assertFalse(service.playerLocale(player).isPresent());
            assertEquals("fr_FR", service.effectiveLocale(player));
            assertTrue(Files.isDirectory(file));
        }
    }

    @Test
    public void failedDefaultWriteDoesNotFallbackOrChangeSelection() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("fr_FR");
        LocalizationSnapshot french = snapshot("fr_FR");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(french);
        AtomicInteger loads = new AtomicInteger();
        try (PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(file,
                () -> List.of("en_US", "fr_FR", "de_DE"), locale::get, active::get, requested -> {
                    loads.incrementAndGet();
                    return snapshot(requested);
                }, (selected, prepared) -> {
                    throw new IOException("Cannot save settings");
                }, Logger.getLogger("language-test")))) {
            assertThrows(ExecutionException.class, () -> service.selectDefault("de_DE").get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
            assertEquals("fr_FR", service.defaultLocale());
            assertSame(french, active.get());
        }
    }

    @Test
    public void invalidationDiscardsInFlightSnapshots() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        UUID player = UUID.randomUUID();
        Files.writeString(file, player + "=fr_FR\n");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (PluginLanguageService service = service(file, new AtomicReference<>("en_US"),
                new AtomicReference<>(ENGLISH), requested -> {
                    entered.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return snapshot(requested);
                })) {
            service.snapshot(player);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.invalidate();
            release.countDown();
            service.clearPlayer(UUID.randomUUID()).get(2, TimeUnit.SECONDS);
            assertSame(ENGLISH, service.snapshot(player));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void cachedUpdateDiscardsOlderInFlightLoadsWithoutEvictingOtherLocales() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        UUID frenchPlayer = UUID.randomUUID();
        UUID germanPlayer = UUID.randomUUID();
        Files.writeString(file, frenchPlayer + "=fr_FR\n" + germanPlayer + "=de_DE\n");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        LocalizationSnapshot updated = LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                List.of(LocaleOverlay.builder("edited", "fr_FR").text(TEXT.id(), "Updated French").build()),
                PluralSelector.oneOther()));
        LocalizationSnapshot german = snapshot("de_DE");
        try (PluginLanguageService service = service(file, new AtomicReference<>("en_US"),
                new AtomicReference<>(ENGLISH), requested -> {
                    entered.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return snapshot(requested);
                })) {
            service.cache("de_DE", german);
            service.snapshot(frenchPlayer);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            service.cache("fr_FR", updated);
            release.countDown();
            service.clearPlayer(UUID.randomUUID()).get(2, TimeUnit.SECONDS);

            assertSame(updated, service.snapshot(frenchPlayer));
            assertSame(german, service.snapshot(germanPlayer));
            assertEquals("fr_FR", service.playerLocale(frenchPlayer).orElseThrow());
        } finally {
            release.countDown();
        }
    }

    @Test
    public void closingCompletesQueuedRequestsWithoutSavingTheirPreferences() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PluginLanguageService service = service(file, new AtomicReference<>("en_US"),
                new AtomicReference<>(ENGLISH), requested -> {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    return snapshot(requested);
                });
        CompletableFuture<Void> loading = service.selectPlayer(UUID.randomUUID(), "fr_FR");
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        CompletableFuture<Void> queued = service.selectDefault("de_DE");
        service.close();
        release.countDown();
        assertTrue(loading.isCompletedExceptionally());
        assertTrue(queued.isCompletedExceptionally());
        assertFalse(Files.exists(file));
    }

    @Test
    public void personalSelectionRepreparesWhenOverridesChangeDuringLoading() throws Exception {
        assertSelectionRepreparesAfterInvalidation(true);
    }

    @Test
    public void serverSelectionRepreparesWhenOverridesChangeDuringLoading() throws Exception {
        assertSelectionRepreparesAfterInvalidation(false);
    }

    @Test
    public void closingWaitsForEnteredDefaultWriterAndReportsItsCommittedResult() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        CountDownLatch writerEntered = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch closeReturned = new CountDownLatch(1);
        PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(file,
                () -> List.of("en_US", "fr_FR"), locale::get, active::get, PluginLanguageServiceTest::snapshot,
                (selected, prepared) -> {
                    writerEntered.countDown();
                    awaitWriterRelease(releaseWriter);
                    locale.set(selected);
                    active.set(prepared);
                }, Logger.getLogger("language-test")));
        Thread closer = new Thread(() -> {
            closeStarted.countDown();
            service.close();
            closeReturned.countDown();
        }, "language-close-test");
        try {
            CompletableFuture<Void> selection = service.selectDefault("fr_FR");
            assertTrue(writerEntered.await(2, TimeUnit.SECONDS));
            closer.start();
            assertTrue(closeStarted.await(2, TimeUnit.SECONDS));
            assertFalse(closeReturned.await(100, TimeUnit.MILLISECONDS));
            releaseWriter.countDown();
            selection.get(2, TimeUnit.SECONDS);
            assertTrue(closeReturned.await(2, TimeUnit.SECONDS));
            assertEquals("fr_FR", locale.get());
            assertEquals("fr_FR", active.get().resolve(TEXT).template());
            assertFalse(selection.isCompletedExceptionally());
        } finally {
            releaseWriter.countDown();
            closer.join(2_000L);
            service.close();
        }
        assertFalse(closer.isAlive());
    }

    @Test
    public void closingInterruptsAnEnteredDefaultWriterBeforeWaitingForItsCommitLock() throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        CountDownLatch writerEntered = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        PluginLanguageService service = new PluginLanguageService(new PluginLanguageService.Options(file,
                () -> List.of("en_US", "fr_FR"), () -> "en_US", () -> ENGLISH,
                PluginLanguageServiceTest::snapshot,
                (selected, prepared) -> {
                    writerEntered.countDown();
                    neverReleased.await();
                }, Logger.getLogger("language-test")));
        CompletableFuture<Void> selection = service.selectDefault("fr_FR");
        assertTrue(writerEntered.await(2, TimeUnit.SECONDS));

        Thread closer = new Thread(service::close, "language-interrupting-close-test");
        closer.start();
        closer.join(2_000L);

        assertFalse(closer.isAlive());
        assertTrue(selection.isCompletedExceptionally());
    }

    @Test
    public void audienceScopesRestoreAfterNestedFailure() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertNull(LanguageAudience.current());
        LanguageAudience.run(first, () -> {
            assertEquals(first, LanguageAudience.current());
            assertThrows(IllegalStateException.class, () -> LanguageAudience.run(second, () -> {
                assertEquals(second, LanguageAudience.current());
                throw new IllegalStateException("failed");
            }));
            assertEquals(first, LanguageAudience.current());
            try (LanguageAudience.Scope scope = LanguageAudience.open(second)) {
                assertEquals(second, LanguageAudience.current());
            }
            assertEquals(first, LanguageAudience.current());
        });
        assertNull(LanguageAudience.current());
    }

    private void assertSelectionRepreparesAfterInvalidation(boolean personal) throws Exception {
        Path file = temporaryFolder.getRoot().toPath().resolve("preferences.properties");
        AtomicReference<String> locale = new AtomicReference<>("en_US");
        AtomicReference<LocalizationSnapshot> active = new AtomicReference<>(ENGLISH);
        AtomicReference<LocalizationSnapshot> content = new AtomicReference<>(snapshot("fr_FR", "before"));
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        UUID player = UUID.randomUUID();
        try (PluginLanguageService service = service(file, locale, active, requested -> {
            LocalizationSnapshot captured = content.get();
            if (loads.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
            }
            return captured;
        })) {
            CompletableFuture<Void> selection = personal
                    ? service.selectPlayer(player, "fr_FR") : service.selectDefault("fr_FR");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            content.set(snapshot("fr_FR", "after"));
            service.invalidate();
            release.countDown();
            selection.get(2, TimeUnit.SECONDS);
            assertEquals("after", service.snapshot(player).resolve(TEXT).template());
            assertEquals(2, loads.get());
            assertEquals("fr_FR", personal ? service.playerLocale(player).orElseThrow() : locale.get());
        } finally {
            release.countDown();
        }
    }

    private static void awaitWriterRelease(CountDownLatch release) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            while (release.getCount() > 0L) {
                long remaining = deadline - System.nanoTime();
                assertTrue("Default writer was not released", remaining > 0L);
                try {
                    assertTrue(release.await(remaining, TimeUnit.NANOSECONDS));
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private PluginLanguageService service(Path file, AtomicReference<String> locale,
                                          AtomicReference<LocalizationSnapshot> active,
                                          PluginLanguageService.SnapshotLoader loader) {
        return new PluginLanguageService(new PluginLanguageService.Options(file,
                () -> List.of("en_US", "fr_FR", "de_DE"), locale::get, active::get, loader,
                (selected, prepared) -> {
                    locale.set(selected);
                    active.set(prepared);
                }, Logger.getLogger("language-test")));
    }

    private static LocalizationSnapshot snapshot(String locale) {
        return snapshot(locale, locale);
    }

    private static LocalizationSnapshot snapshot(String locale, String text) {
        return LocalizationSnapshot.create(new LocalizationCandidate(CATALOG,
                List.of(LocaleOverlay.builder(locale).text(TEXT.id(), text).build()), PluralSelector.oneOther()));
    }
}
