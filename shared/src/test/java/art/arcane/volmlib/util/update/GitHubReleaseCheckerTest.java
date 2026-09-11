package art.arcane.volmlib.util.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubReleaseCheckerTest {
    @Test
    public void comparesThePluginVersionWithoutMinecraftOrBuildSuffixes() {
        assertFalse(GitHubReleaseChecker.isNewer("1.0", "2.0.0-1.20.1-26.2"));
        assertFalse(GitHubReleaseChecker.isNewer("v2.0.0", "2.0.0-1.20.1-26.2"));
        assertTrue(GitHubReleaseChecker.isNewer("v2.0.1", "2.0.0-1.20.1-26.2"));
        assertTrue(GitHubReleaseChecker.isNewer("2.10.0", "2.9.0"));
        assertFalse(GitHubReleaseChecker.isNewer("2.9.0", "2.10.0"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0", "2.0.0"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0+build.10"));
        assertTrue(GitHubReleaseChecker.isNewer("3.0.0", "2.99.99-SNAPSHOT"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0.0", "3.0.0-SNAPSHOT"));
        assertFalse(GitHubReleaseChecker.isNewer("latest", "1.0"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0", "development"));
        assertTrue(GitHubReleaseChecker.isNewer("99999999999999999999999.0", "2.0"));
    }

    @Test
    public void enablingFetchesOnceAndConcurrentCallersShareTheCachedResult() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.block();
            try (GitHubReleaseChecker checker = fixture.checker()) {
                assertTrue(checker.check().get(1, TimeUnit.SECONDS).isEmpty());
                assertEquals(0, fixture.requests.get());
                checker.setEnabled(true);
                assertTrue(fixture.entered.await(2, TimeUnit.SECONDS));
                List<CompletableFuture<Optional<GitHubReleaseChecker.Release>>> checks = new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    checks.add(checker.check());
                }
                assertFalse(checks.get(0).isDone());
                checks.get(0).cancel(false);
                fixture.unblock();
                for (int index = 1; index < checks.size(); index++) {
                    GitHubReleaseChecker.Release release = checks.get(index).get(3, TimeUnit.SECONDS).orElseThrow();
                    assertEquals("v2.0.1", release.tagName());
                    assertEquals("https://github.com/VolmitSoftware/ShapedPortals/releases/tag/v2.0.1", release.url());
                }
                assertEquals(checker.check().get(), checker.check().get());
                checker.setEnabled(true);
                assertEquals(1, fixture.requests.get());
                assertEquals("/repos/VolmitSoftware/ShapedPortals/releases/latest", fixture.path.get());
                assertEquals("application/vnd.github+json", fixture.accept.get());
                assertEquals("2022-11-28", fixture.apiVersion.get());
            }
        }
    }

    @Test
    public void disablingCompletesPendingChecksAndDiscardsTheirResultsAfterReenable() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.block();
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertTrue(fixture.entered.await(2, TimeUnit.SECONDS));
                CompletableFuture<Optional<GitHubReleaseChecker.Release>> old = checker.check();
                checker.setEnabled(false);
                assertTrue(old.get(1, TimeUnit.SECONDS).isEmpty());
                assertTrue(checker.check().get(1, TimeUnit.SECONDS).isEmpty());
                fixture.status.set(404);
                checker.setEnabled(true);
                CompletableFuture<Optional<GitHubReleaseChecker.Release>> current = checker.check();
                fixture.unblock();
                assertTrue(current.get(3, TimeUnit.SECONDS).isEmpty());
                assertTrue(checker.check().get().isEmpty());
                assertEquals(2, fixture.requests.get());
            }
        }
    }

    @Test
    public void closeCompletesPendingChecksAndCannotBeReenabled() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.block();
            GitHubReleaseChecker checker = fixture.checker();
            try {
                checker.setEnabled(true);
                assertTrue(fixture.entered.await(2, TimeUnit.SECONDS));
                CompletableFuture<Optional<GitHubReleaseChecker.Release>> pending = checker.check();
                checker.close();
                assertTrue(pending.get(1, TimeUnit.SECONDS).isEmpty());
                checker.setEnabled(true);
                assertTrue(checker.check().get(1, TimeUnit.SECONDS).isEmpty());
                fixture.unblock();
                assertEquals(1, fixture.requests.get());
            } finally {
                checker.close();
            }
        }
    }

    @Test
    public void noReleaseAndDraftOrPrereleaseResponsesNeverNotify() throws Exception {
        assertEmpty(404, "{}");
        assertEmpty(200, release("1.0", false, false));
        assertEmpty(200, release("2.0.0", false, false));
        assertEmpty(200, release("3.0", true, false));
        assertEmpty(200, release("3.0", false, true));
    }

    @Test
    public void transportAndMalformedResponsesAreCachedAndLoggedOnce() throws Exception {
        for (String body : List.of("{}", "[]", "not json", release("v2.0.1", false, false).replace("https://github.com/", "https://example.com/"))) {
            try (Fixture fixture = new Fixture()) {
                fixture.body.set(body);
                try (GitHubReleaseChecker checker = fixture.checker()) {
                    checker.setEnabled(true);
                    assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                    assertTrue(checker.check().get().isEmpty());
                    assertEquals(1, fixture.requests.get());
                    assertEquals(1, fixture.logs.size());
                    assertTrue(fixture.logs.get(0).getThrown() != null);
                }
            }
        }
        try (Fixture fixture = new Fixture()) {
            fixture.status.set(403);
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                assertTrue(checker.check().get().isEmpty());
                assertEquals(1, fixture.requests.get());
                assertEquals(1, fixture.logs.size());
                assertTrue(fixture.logs.get(0).getThrown().getMessage().contains("403"));
            }
        }
    }

    @Test
    public void oversizedResponsesAreRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.body.set(" ".repeat(1024 * 1024 + 1));
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                assertEquals(1, fixture.logs.size());
                assertTrue(fixture.logs.get(0).getThrown().getMessage().contains("exceeds"));
            }
        }
    }

    @Test
    public void periodicRefreshRetriesFailuresWithoutRepeatingWarnings() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.status.set(503);
            try (GitHubReleaseChecker checker = fixture.checker(Duration.ofMillis(50))) {
                checker.setEnabled(true);
                assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (fixture.requests.get() < 3 && System.nanoTime() < deadline) {
                    Thread.sleep(10);
                }
                assertTrue(fixture.requests.get() >= 3);
                assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                assertEquals(1, fixture.logs.size());
                fixture.status.set(200);
                Optional<GitHubReleaseChecker.Release> latest = Optional.empty();
                while (latest.isEmpty() && System.nanoTime() < deadline) {
                    latest = checker.check().get(3, TimeUnit.SECONDS);
                    Thread.sleep(10);
                }
                assertTrue(latest.isPresent());
            }
        }
    }

    private static void assertEmpty(int status, String body) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.status.set(status);
            fixture.body.set(body);
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                assertTrue(fixture.logs.isEmpty());
            }
        }
    }

    private static String release(String tag, boolean draft, boolean prerelease) {
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"https://github.com/VolmitSoftware/ShapedPortals/releases/tag/"
                + tag + "\",\"draft\":" + draft + ",\"prerelease\":" + prerelease + "}";
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger status = new AtomicInteger(200);
        private final AtomicReference<String> body = new AtomicReference<>(release("v2.0.1", false, false));
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final AtomicReference<String> apiVersion = new AtomicReference<>();
        private final List<LogRecord> logs = new CopyOnWriteArrayList<>();
        private final Logger logger = Logger.getAnonymousLogger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private CountDownLatch blocked = new CountDownLatch(0);

        private Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", this::handle);
            server.start();
            logger.setUseParentHandlers(false);
            logger.addHandler(new LogHandler(logs));
        }

        private GitHubReleaseChecker checker() {
            return checker(Duration.ofHours(1));
        }

        private GitHubReleaseChecker checker(Duration interval) {
            GitHubReleaseChecker.Options options = new GitHubReleaseChecker.Options("VolmitSoftware", "ShapedPortals", "2.0.0-1.20.1-26.2", logger);
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/repos/VolmitSoftware/ShapedPortals/releases/latest");
            return new GitHubReleaseChecker(options, new GitHubReleaseChecker.Access(endpoint, interval));
        }

        private void block() {
            blocked = new CountDownLatch(1);
        }

        private void unblock() {
            blocked.countDown();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            apiVersion.set(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
            int responseStatus = status.get();
            byte[] response = body.get().getBytes(StandardCharsets.UTF_8);
            entered.countDown();
            try {
                if (!blocked.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Release fixture timed out");
                }
                exchange.sendResponseHeaders(responseStatus, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            unblock();
            server.stop(0);
        }
    }

    private static final class LogHandler extends Handler {
        private final List<LogRecord> logs;

        private LogHandler(List<LogRecord> logs) {
            this.logs = logs;
        }

        @Override
        public void publish(LogRecord record) {
            logs.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
