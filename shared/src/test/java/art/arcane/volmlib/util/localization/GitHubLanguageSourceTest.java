package art.arcane.volmlib.util.localization;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GitHubLanguageSourceTest {
    private static final String REVISION = "30cea6163bc3a0e790d3aeb6816e79a67cac615f";
    private static final LanguageSourceManifest MANIFEST = new LanguageSourceManifest(
            "VolmitSoftware/Test", "feature/next", "languages", ".toml", Set.of("fr_FR"));

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("[{\"sha\":\"" + REVISION + "\"}]");
    private final Map<String, String> headers = new ConcurrentHashMap<>();
    private final AtomicReference<String> seenIfNoneMatch = new AtomicReference<>();
    private final AtomicReference<String> seenQuery = new AtomicReference<>();
    private final AtomicReference<byte[]> file = new AtomicReference<>("greeting = \"Bonjour\"\n".getBytes(StandardCharsets.UTF_8));
    private final AtomicInteger fileStatus = new AtomicInteger(200);
    private GitHubLanguageSource source;

    @Before
    public void setup() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", this::api);
        server.createContext("/raw/", this::raw);
        server.start();
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        source = new GitHubLanguageSource(MANIFEST, "Test", root.resolve("api/"), root.resolve("raw/"));
    }

    @After
    public void cleanup() {
        server.stop(0);
    }

    @Test
    public void readsTheLatestCommitForTheLanguagePathWithConditionalHeaders() throws IOException {
        headers.put("ETag", "W/\"abc\"");

        GitHubLanguageSource.RevisionCheck check = source.latestRevision(null);

        assertEquals(Optional.of(REVISION), check.revision());
        assertEquals(Optional.of("W/\"abc\""), check.etag());
        assertFalse(check.unchanged());
        assertEquals("sha=feature/next&path=languages&per_page=1", seenQuery.get());
        assertEquals("repos/VolmitSoftware/Test/commits?sha=feature/next&path=languages&per_page=1",
                source.commitsUri().toString().substring(source.commitsUri().toString().indexOf("repos/")));

        status.set(304);
        GitHubLanguageSource.RevisionCheck unchanged = source.latestRevision("W/\"abc\"");

        assertTrue(unchanged.unchanged());
        assertTrue(unchanged.revision().isEmpty());
        assertEquals(Optional.of("W/\"abc\""), unchanged.etag());
        assertEquals("W/\"abc\"", seenIfNoneMatch.get());
    }

    @Test
    public void rateLimitsBecomeRetryLaterWithTheAdvertisedDelay() throws IOException {
        status.set(403);
        headers.put("Retry-After", "120");
        try {
            source.latestRevision(null);
            fail("Expected retry later");
        } catch (GitHubLanguageSource.RetryLater retryLater) {
            assertEquals(120_000L, retryLater.delayMillis());
        }
        status.set(429);
        headers.clear();
        try {
            source.latestRevision(null);
            fail("Expected retry later");
        } catch (GitHubLanguageSource.RetryLater retryLater) {
            assertEquals(60_000L, retryLater.delayMillis());
        }
    }

    @Test
    public void invalidResponsesFailWithoutARevision() {
        for (String invalid : new String[]{"not json", "[]", "{\"sha\":\"x\"}", "[{\"sha\":\"short\"}]", "[{}]"}) {
            body.set(invalid);
            try {
                source.latestRevision(null);
                fail("Accepted " + invalid);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("GitHub language source"));
            }
        }
        status.set(404);
        body.set("[{\"sha\":\"" + REVISION + "\"}]");
        try {
            source.latestRevision(null);
            fail("Accepted 404");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not found"));
        }
        status.set(500);
        try {
            source.latestRevision(null);
            fail("Accepted 500");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTP 500"));
        }
    }

    @Test
    public void fetchesFilesAtAnImmutableRevision() throws IOException {
        byte[] bytes = source.fetch("fr_fr", REVISION);

        assertArrayEquals(file.get(), bytes);
        assertTrue(source.fileUri("fr_FR", REVISION).toString().endsWith("/raw/VolmitSoftware/Test/" + REVISION
                + "/languages/fr_FR.toml"));
        try {
            source.fileUri("fr_FR", "main");
            fail("Accepted a branch as a revision");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("40-character"));
        }
        fileStatus.set(404);
        try {
            source.fetch("fr_FR", REVISION);
            fail("Accepted 404");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("HTTP 404"));
        }
        fileStatus.set(200);
        file.set(new byte[GitHubLanguageSource.MAXIMUM_FILE_BYTES + 1]);
        try {
            source.fetch("fr_FR", REVISION);
            fail("Accepted an oversized file");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exceeds"));
        }
    }

    private void api(HttpExchange exchange) throws IOException {
        seenQuery.set(exchange.getRequestURI().getRawQuery());
        seenIfNoneMatch.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
        assertEquals("Test-Language-Update", exchange.getRequestHeaders().getFirst("User-Agent"));
        assertEquals("application/vnd.github+json", exchange.getRequestHeaders().getFirst("Accept"));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            exchange.getResponseHeaders().set(header.getKey(), header.getValue());
        }
        int code = status.get();
        if (code == 304) {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }
        byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void raw(HttpExchange exchange) throws IOException {
        int code = fileStatus.get();
        byte[] response = code == 200 ? file.get() : new byte[0];
        exchange.sendResponseHeaders(code, response.length == 0 ? -1 : response.length);
        if (response.length > 0) {
            exchange.getResponseBody().write(response);
        }
        exchange.close();
    }
}
