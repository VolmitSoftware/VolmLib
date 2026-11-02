package art.arcane.volmlib.util.localization;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LanguageUpstreamSyncTest {
    private static final String REVISION_ONE = "1111111111111111111111111111111111111111";
    private static final String REVISION_TWO = "2222222222222222222222222222222222222222";
    private static final MessageCatalog CATALOG = MessageCatalog.of("en_US",
            TextKey.of("greeting", "Hello {name}"),
            TextKey.of("farewell", "Goodbye"),
            TextKey.of("menu.title", "Menu"));
    private static final LanguageSourceManifest MANIFEST = new LanguageSourceManifest(
            "VolmitSoftware/Test", "main", "languages", ".toml", Set.of("fr_FR", "de_DE"));

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private HttpServer server;
    private final AtomicReference<String> revision = new AtomicReference<>(REVISION_ONE);
    private final AtomicInteger apiStatus = new AtomicInteger(200);
    private final AtomicInteger apiCalls = new AtomicInteger();
    private final AtomicInteger rawCalls = new AtomicInteger();
    private final Map<String, String> files = new ConcurrentHashMap<>();
    private final AtomicReference<String> englishReference = new AtomicReference<>();
    private final List<String> selfWrites = new ArrayList<>();
    private final List<String> applied = new ArrayList<>();
    private LanguageDirectory directory;
    private LanguageUpstreamStore store;
    private LanguageUpstreamSync sync;

    @Before
    public void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", this::api);
        server.createContext("/raw/", this::raw);
        server.start();
        files.put(REVISION_ONE + "/fr_FR", "# fr v1\n\ngreeting = \"Bonjour {name}\"\nfarewell = \"Au revoir\"\n");
        files.put(REVISION_ONE + "/de_DE", "# de v1\n\ngreeting = \"Hallo {name}\"\n");
        englishReference.set(LanguageReferenceRenderer.render(CATALOG, List.of("Test en_US")));
        directory = new LanguageDirectory(temporaryFolder.getRoot().toPath().resolve("languages"));
        store = new LanguageUpstreamStore(directory, MANIFEST);
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        sync = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                "Test", CATALOG, directory, MANIFEST,
                new GitHubLanguageSource(MANIFEST, "Test", root.resolve("api/"), root.resolve("raw/")),
                store, englishReference::get,
                (locale, content) -> TomlLanguageParser.parseValidText(content, CATALOG),
                null,
                (file, content) -> selfWrites.add(file.getFileName().toString()),
                (locale, content, result) -> applied.add(locale + ":" + result.summary()),
                Logger.getLogger("LanguageUpstreamSyncTest")), Duration.ZERO);
    }

    @After
    public void cleanup() {
        sync.close();
        server.stop(0);
    }

    @Test
    public void installsAMissingLocaleFromTheBranchHead() throws IOException {
        String content = sync.install("fr_fr");

        assertEquals("Bonjour {name}", TomlLanguageParser.parseText(content).get("greeting"));
        assertEquals(content, directory.read("fr_FR"));
        assertEquals(Optional.of(files.get(REVISION_ONE + "/fr_FR")), store.readUpstream("fr_FR"));
        assertEquals(Optional.of(REVISION_ONE), store.load().revision());
        assertTrue(store.load().hashes().containsKey("fr_FR"));
        assertEquals(List.of("fr_FR.toml"), selfWrites);
        assertEquals(content, sync.install("fr_FR"));
        assertEquals(1, rawCalls.get());
    }

    @Test
    public void mergesUpstreamChangesWhileKeepingLocalEdits() throws Exception {
        sync.install("fr_FR");
        sync.mergeEnglish();
        Path file = directory.languageFile("fr_FR");
        Files.writeString(file, Files.readString(file).replace("Bonjour {name}", "Salut {name}"));
        revision.set(REVISION_TWO);
        files.put(REVISION_TWO + "/fr_FR",
                "# fr v2\n\ngreeting = \"Bonjour, {name}\"\nfarewell = \"Adieu\"\n\n[menu]\ntitle = \"Carte\"\n");
        selfWrites.clear();

        LanguageUpstreamSync.SyncReport report = sync.checkAsync().get(10L, TimeUnit.SECONDS);

        assertTrue(report.failure().isEmpty());
        assertEquals(Optional.of(REVISION_TWO), report.revision());
        assertFalse(report.unchanged());
        assertEquals(List.of("fr_FR"), report.changedLocales());
        LanguageCatalogMerge.MergeResult result = report.merged().get("fr_FR");
        assertEquals(List.of("greeting"), result.kept());
        assertEquals(List.of("farewell"), result.updated());
        assertEquals(List.of("menu.title"), result.added());
        Map<String, String> values = TomlLanguageParser.parseText(directory.read("fr_FR"));
        assertEquals("Salut {name}", values.get("greeting"));
        assertEquals("Adieu", values.get("farewell"));
        assertEquals("Carte", values.get("menu.title"));
        assertTrue(directory.read("fr_FR").startsWith("# fr v2\n"));
        assertEquals(Optional.of(files.get(REVISION_TWO + "/fr_FR")), store.readUpstream("fr_FR"));
        assertEquals(List.of("fr_FR.toml"), selfWrites);
        assertEquals(List.of("fr_FR:1 added, 1 updated, 0 removed, 1 kept local"), applied);
        assertFalse(directory.exists("de_DE"));
        assertEquals(Optional.of(REVISION_TWO), store.load().revision());
    }

    @Test
    public void unchangedRevisionWritesNothingAndUsesConditionalRequests() throws Exception {
        sync.install("fr_FR");
        sync.mergeEnglish();
        byte[] before = Files.readAllBytes(directory.languageFile("fr_FR"));
        selfWrites.clear();

        LanguageUpstreamSync.SyncReport first = sync.checkAsync().get(10L, TimeUnit.SECONDS);
        LanguageUpstreamSync.SyncReport second = sync.checkAsync().get(10L, TimeUnit.SECONDS);

        assertTrue(first.unchanged());
        assertTrue(second.unchanged());
        assertTrue(first.changedLocales().isEmpty());
        assertEquals(Optional.of(REVISION_ONE), second.revision());
        assertEquals(before.length, Files.readAllBytes(directory.languageFile("fr_FR")).length);
        assertTrue(selfWrites.isEmpty());
        assertTrue(applied.isEmpty());
        assertEquals(1, rawCalls.get());
        assertTrue(store.load().etag().isPresent());
        assertEquals(1, apiCalls.get() - 2);
    }

    @Test
    public void installedLocalesWithoutASidecarAreReconciledOnTheNextCheck() throws Exception {
        directory.prepare();
        Files.writeString(directory.languageFile("de_DE"), "greeting = \"Servus {name}\"\n");

        LanguageUpstreamSync.SyncReport report = sync.checkAsync().get(10L, TimeUnit.SECONDS);

        assertTrue(report.failure().isEmpty());
        assertEquals(List.of("de_DE"), List.copyOf(report.merged().keySet()).stream()
                .filter(locale -> !locale.equals("en_US")).toList());
        assertEquals("Servus {name}", TomlLanguageParser.parseText(directory.read("de_DE")).get("greeting"));
        assertEquals(Optional.of(files.get(REVISION_ONE + "/de_DE")), store.readUpstream("de_DE"));
        assertTrue(report.merged().get("de_DE").kept().contains("greeting"));
    }

    @Test
    public void sourceFailuresLeaveFilesAndStateIntactAndCoolDown() throws Exception {
        sync.install("fr_FR");
        byte[] before = Files.readAllBytes(directory.languageFile("fr_FR"));
        LanguageUpstreamStore.State state = store.load();
        apiStatus.set(500);
        int calls = apiCalls.get();

        LanguageUpstreamSync.SyncReport failed = sync.checkAsync().get(10L, TimeUnit.SECONDS);
        LanguageUpstreamSync.SyncReport cooled = sync.checkAsync().get(10L, TimeUnit.SECONDS);

        assertTrue(failed.failure().orElse("").contains("HTTP 500"));
        assertEquals(Optional.of(REVISION_ONE), failed.revision());
        assertTrue(cooled.unchanged());
        assertEquals(failed.failure(), cooled.failure());
        assertEquals(calls + 1, apiCalls.get());
        assertEquals(before.length, Files.readAllBytes(directory.languageFile("fr_FR")).length);
        assertTrue(cooled.coolingDown());
        assertEquals(state.revision(), store.load().revision());
        assertEquals(state.hashes(), store.load().hashes());
        assertTrue(store.load().checkedMillis() >= state.checkedMillis());
        assertEquals(Optional.of(failed.failure().get()), sync.status().lastError());
    }

    @Test
    public void minimumIntervalBlocksRepeatedChecksAndSurvivesRestart() throws Exception {
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        LanguageUpstreamSync.Options options = new LanguageUpstreamSync.Options(
                "Test", CATALOG, directory, MANIFEST,
                new GitHubLanguageSource(MANIFEST, "Test", root.resolve("api/"), root.resolve("raw/")),
                store, englishReference::get,
                (locale, content) -> TomlLanguageParser.parseValidText(content, CATALOG),
                null, null, null, Logger.getLogger("interval"));
        LanguageUpstreamSync limited = new LanguageUpstreamSync(options, Duration.ofMinutes(5L));
        try {
            LanguageUpstreamSync.SyncReport first = limited.checkAsync().get(10L, TimeUnit.SECONDS);
            LanguageUpstreamSync.SyncReport second = limited.checkAsync().get(10L, TimeUnit.SECONDS);

            assertFalse(first.coolingDown());
            assertEquals(Optional.of(REVISION_ONE), first.revision());
            assertTrue(second.coolingDown());
            assertTrue(second.unchanged());
            assertEquals(Optional.of(REVISION_ONE), second.revision());
            assertTrue(second.cooldownRemainingMillis() > 0L && second.cooldownRemainingMillis() <= 300_000L);
            assertTrue(second.cooldownRemaining().matches("[0-9]+m( [0-9]+s)?|[0-9]+s"));
            assertEquals(1, apiCalls.get());
        } finally {
            limited.close();
        }
        LanguageUpstreamSync restarted = new LanguageUpstreamSync(options, Duration.ofMinutes(5L));
        try {
            LanguageUpstreamSync.SyncReport afterRestart = restarted.checkAsync().get(10L, TimeUnit.SECONDS);

            assertTrue(afterRestart.coolingDown());
            assertEquals(1, apiCalls.get());
        } finally {
            restarted.close();
        }
    }

    @Test
    public void englishReferenceIsCreatedAndMergedOffline() throws Exception {
        apiStatus.set(500);

        LanguageUpstreamSync.SyncReport first = sync.checkAsync().get(10L, TimeUnit.SECONDS);

        assertTrue(directory.exists("en_US"));
        assertEquals("Hello {name}", TomlLanguageParser.parseText(directory.read("en_US")).get("greeting"));
        assertTrue(first.merged().isEmpty());
        assertEquals(List.of("en_US.toml"), selfWrites);

        Path english = directory.languageFile("en_US");
        Files.writeString(english, Files.readString(english).replace("Hello {name}", "Hi {name}"));
        MessageCatalog grown = MessageCatalog.of("en_US",
                TextKey.of("greeting", "Hello {name}"),
                TextKey.of("farewell", "Goodbye"),
                TextKey.of("menu.title", "Menu"),
                TextKey.of("menu.close", "Close"));
        String grownReference = LanguageReferenceRenderer.render(grown, List.of("Test en_US v2"));
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        LanguageUpstreamSync upgraded = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                "Test", grown, directory, MANIFEST,
                new GitHubLanguageSource(MANIFEST, "Test", root.resolve("api/"), root.resolve("raw/")),
                store, () -> grownReference,
                (locale, content) -> TomlLanguageParser.parseValidText(content, grown),
                null, null,
                (locale, content, result) -> applied.add(locale + ":" + result.summary()),
                Logger.getLogger("upgraded")), Duration.ZERO);
        try {
            Optional<LanguageCatalogMerge.MergeResult> merged = upgraded.mergeEnglish();

            assertTrue(merged.isPresent());
            assertEquals(List.of("greeting"), merged.get().kept());
            assertEquals(List.of("menu.close"), merged.get().added());
            Map<String, String> values = TomlLanguageParser.parseText(directory.read("en_US"));
            assertEquals("Hi {name}", values.get("greeting"));
            assertEquals("Close", values.get("menu.close"));
            assertTrue(directory.read("en_US").startsWith("# Test en_US v2\n"));
            assertEquals(List.of("en_US:" + merged.get().summary()), applied);
            assertTrue(upgraded.mergeEnglish().isEmpty());
        } finally {
            upgraded.close();
        }
    }

    @Test
    public void invalidMergedContentIsRejectedPerLocale() throws Exception {
        sync.install("fr_FR");
        revision.set(REVISION_TWO);
        files.put(REVISION_TWO + "/fr_FR", "greeting = \"Bonjour {name}\"\nfarewell = \"[broken\"\n");
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        LanguageUpstreamSync strict = new LanguageUpstreamSync(new LanguageUpstreamSync.Options(
                "Test", CATALOG, directory, MANIFEST,
                new GitHubLanguageSource(MANIFEST, "Test", root.resolve("api/"), root.resolve("raw/")),
                store, englishReference::get,
                (locale, content) -> {
                    if (content.contains("[broken")) {
                        throw new IOException("markup rejected");
                    }
                },
                null, null, null, Logger.getLogger("strict")), Duration.ZERO);
        try {
            LanguageUpstreamSync.SyncReport report = strict.checkAsync().get(10L, TimeUnit.SECONDS);

            assertTrue(report.failure().isEmpty());
            assertTrue(report.failures().get("fr_FR").contains("invalid"));
            assertEquals("Au revoir", TomlLanguageParser.parseText(directory.read("fr_FR")).get("farewell"));
            assertEquals(Optional.of(files.get(REVISION_ONE + "/fr_FR")), store.readUpstream("fr_FR"));
        } finally {
            strict.close();
        }
    }

    @Test
    public void closedSyncReportsFailureWithoutWork() throws Exception {
        sync.close();

        LanguageUpstreamSync.SyncReport report = sync.checkAsync().get(1L, TimeUnit.SECONDS);

        assertTrue(report.failure().orElse("").contains("closed"));
        assertEquals(0, apiCalls.get());
    }

    private void api(HttpExchange exchange) throws IOException {
        apiCalls.incrementAndGet();
        int code = apiStatus.get();
        String etag = "\"" + revision.get() + "\"";
        if (code == 200 && etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("ETag", etag);
        byte[] response = ("[{\"sha\":\"" + revision.get() + "\"}]").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void raw(HttpExchange exchange) throws IOException {
        rawCalls.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        String key = parts[4] + "/" + parts[6].replace(".toml", "");
        String content = files.get(key);
        if (content == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        byte[] response = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
