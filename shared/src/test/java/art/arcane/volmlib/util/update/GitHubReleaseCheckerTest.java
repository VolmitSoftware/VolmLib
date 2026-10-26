package art.arcane.volmlib.util.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
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
import static org.junit.Assert.assertNull;
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
                    assertEquals(tagUrl("v2.0.1"), release.url());
                }
                assertEquals(checker.check().get(), checker.check().get());
                checker.setEnabled(true);
                assertEquals(1, fixture.requests.get());
                assertEquals("/VolmitSoftware/ShapedPortals/releases/latest", fixture.path.get());
                assertEquals("HEAD", fixture.method.get());
            }
        }
    }

    @Test
    public void stableReleaseSupersedesRecognizedPrereleasesButNotMinecraftSuffixes() {
        assertTrue(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0-rc.1"));
        assertTrue(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0-SNAPSHOT"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0-1.20.1-26.2"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0+build.9"));
    }

    @Test
    public void wildcardMinecraftSuffixUsesThePluginVersionInReports() throws Exception {
        for (String installed : List.of("2.0.0-26.x", "2.0.0-1.20.1-26.x")) {
            for (String available : List.of("1.0.2", "2.0.0", "2.0.1")) {
                try (Fixture fixture = new Fixture();
                     GitHubReleaseChecker checker = fixture.checker(installed, Duration.ofHours(1))) {
                    fixture.location.set(tagUrl(available));
                    checker.setEnabled(true);
                    GitHubReleaseChecker.Status expected = available.equals("2.0.1")
                            ? GitHubReleaseChecker.Status.UPDATE : GitHubReleaseChecker.Status.CURRENT;
                    assertEquals(installed + " -> " + available, expected,
                            checker.checkReport(false).get(3, TimeUnit.SECONDS).status());
                }
            }
        }
        assertTrue(GitHubReleaseChecker.isNewer("2.0.0-26.x", "2.0.0-rc.1"));
        assertFalse(GitHubReleaseChecker.isNewer("2.0.0", "2.0.0-26.x"));
    }

    @Test
    public void readsMinecraftRangeTagsFromTheRedirect() throws Exception {
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker("4.3.4-26.1.2-26.3", Duration.ofHours(1))) {
            fixture.location.set(tagUrl("4.3.5-26.1.2-26.3"));
            checker.setEnabled(true);
            GitHubReleaseChecker.Report report = checker.checkReport(false).get(3, TimeUnit.SECONDS);
            assertEquals(GitHubReleaseChecker.Status.UPDATE, report.status());
            assertEquals("4.3.5-26.1.2-26.3", report.release().tagName());
        }
    }

    @Test
    public void failurePreservesLastSuccessfulUpdateAndReportsStaleness() throws Exception {
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker(Duration.ofMillis(50))) {
            checker.setEnabled(true);
            GitHubReleaseChecker.Report success = checker.checkReport(false).get(3, TimeUnit.SECONDS);
            assertEquals(GitHubReleaseChecker.Status.UPDATE, success.status());
            fixture.status.set(503);
            GitHubReleaseChecker.Report failed = checkUntil(checker, GitHubReleaseChecker.Status.FAILED);
            assertEquals(GitHubReleaseChecker.Status.FAILED, failed.status());
            assertEquals(success.release(), failed.release());
            assertEquals(success.lastSuccessMillis(), failed.lastSuccessMillis());
            assertTrue(checker.check().get(3, TimeUnit.SECONDS).isPresent());
            checker.setEnabled(false);
            assertEquals(GitHubReleaseChecker.Status.DISABLED, checker.snapshot().status());
            assertTrue(checker.check().get().isEmpty());
        }
    }

    @Test
    public void manualChecksWaitForTheCooldownAndRateLimits() throws Exception {
        for (int status : List.of(302, 429)) {
            try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker()) {
                fixture.status.set(status);
                checker.setEnabled(true);
                GitHubReleaseChecker.Report report = checker.checkReport(true).get(3, TimeUnit.SECONDS);
                for (int index = 0; index < 10; index++) {
                    assertEquals(report, checker.checkReport(true).get(3, TimeUnit.SECONDS));
                }
                assertEquals(1, fixture.requests.get());
                assertEquals(status == 302 ? GitHubReleaseChecker.Status.UPDATE : GitHubReleaseChecker.Status.FAILED, report.status());
            }
        }
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker(Duration.ofMillis(20))) {
            checker.setEnabled(true);
            checker.checkReport(false).get(3, TimeUnit.SECONDS);
            Thread.sleep(60L);
            checker.checkReport(true).get(3, TimeUnit.SECONDS);
            assertEquals(2, fixture.requests.get());
            fixture.status.set(429);
            Thread.sleep(60L);
            checker.checkReport(true).get(3, TimeUnit.SECONDS);
            Thread.sleep(60L);
            checker.checkReport(true).get(3, TimeUnit.SECONDS);
            assertEquals(3, fixture.requests.get());
        }
    }

    @Test
    public void failureWarningNamesWhenManualChecksResume() throws Exception {
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker(Duration.ofMinutes(5))) {
            fixture.status.set(503);
            checker.setEnabled(true);
            checker.checkReport(false).get(3, TimeUnit.SECONDS);
            assertTrue(fixture.logs.get(0).getMessage().endsWith("/volmit plugins updates can check again in 5 minutes."));
        }
    }

    @Test
    public void waitTimesReadAsHoursAndMinutes() {
        assertEquals("1 minute", GitHubReleaseChecker.describeDelay(10L));
        assertEquals("5 minutes", GitHubReleaseChecker.describeDelay(TimeUnit.MINUTES.toMillis(5)));
        assertEquals("59 minutes", GitHubReleaseChecker.describeDelay(TimeUnit.MINUTES.toMillis(59)));
        assertEquals("1 hour", GitHubReleaseChecker.describeDelay(TimeUnit.HOURS.toMillis(1)));
        assertEquals("1 hour 1 minute", GitHubReleaseChecker.describeDelay(TimeUnit.MINUTES.toMillis(61)));
        assertEquals("4 hours 42 minutes", GitHubReleaseChecker.describeDelay(TimeUnit.SECONDS.toMillis(16_920)));
        assertEquals("168 hours", GitHubReleaseChecker.describeDelay(TimeUnit.DAYS.toMillis(7)));
    }

    @Test
    public void unrecognizedSuffixIsUnknownRatherThanCurrentOrAnUpdate() throws Exception {
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker()) {
            fixture.location.set(tagUrl("3.0-custom"));
            checker.setEnabled(true);
            assertEquals(GitHubReleaseChecker.Status.UNKNOWN, checker.checkReport(false).get(3, TimeUnit.SECONDS).status());
            assertTrue(checker.check().get().isEmpty());
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
    public void missingRepositoryOrReleaseNeverNotifies() throws Exception {
        assertNoRelease(404, null);
        assertNoRelease(302, "https://github.com/VolmitSoftware/ShapedPortals/releases");
        assertCurrent(tagUrl("1.0"));
        assertCurrent(tagUrl("2.0.0"));
    }

    @Test
    public void invalidRedirectsAndHttpFailuresLogOneLineOnce() throws Exception {
        List<String> locations = new ArrayList<>();
        locations.add(null);
        locations.add("https://example.com/VolmitSoftware/ShapedPortals/releases/tag/v2.0.1");
        locations.add("http://github.com/VolmitSoftware/ShapedPortals/releases/tag/v2.0.1");
        locations.add("https://github.com/VolmitSoftware/Iris/releases/tag/v2.0.1");
        locations.add("https://github.com/VolmitSoftware/ShapedPortals/releases/tag/");
        locations.add("/VolmitSoftware/ShapedPortals/releases/tag/v2.0.1");
        for (String location : locations) {
            try (Fixture fixture = new Fixture()) {
                fixture.location.set(location);
                try (GitHubReleaseChecker checker = fixture.checker()) {
                    checker.setEnabled(true);
                    assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                    assertEquals(String.valueOf(location), GitHubReleaseChecker.Status.FAILED, checker.snapshot().status());
                    assertEquals(1, fixture.logs.size());
                    assertNull(fixture.logs.get(0).getThrown());
                }
            }
        }
        for (int status : List.of(200, 403, 429, 500)) {
            try (Fixture fixture = new Fixture()) {
                fixture.status.set(status);
                fixture.retryAfter.set("7200");
                try (GitHubReleaseChecker checker = fixture.checker()) {
                    checker.setEnabled(true);
                    assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
                    assertEquals(1, fixture.requests.get());
                    assertEquals(1, fixture.logs.size());
                    LogRecord record = fixture.logs.get(0);
                    assertNull(record.getThrown());
                    assertTrue(record.getMessage().contains("HTTP " + status));
                    assertTrue(record.getMessage().contains("VolmitSoftware/ShapedPortals"));
                    if (status == 429) {
                        assertTrue(record.getMessage().contains("GitHub rate limited the request (HTTP 429)."));
                    }
                    if (status == 403) {
                        assertTrue(record.getMessage().contains("GitHub refused the request (HTTP 403)."));
                    }
                    if (status == 403 || status == 429) {
                        assertTrue(record.getMessage().endsWith("can check again in 2 hours."));
                    }
                }
            }
        }
    }

    @Test
    public void runningServersNeverRecheckOnTheirOwn() throws Exception {
        try (Fixture fixture = new Fixture(); GitHubReleaseChecker checker = fixture.checker(Duration.ofMillis(10))) {
            fixture.status.set(503);
            checker.setEnabled(true);
            assertTrue(checker.check().get(3, TimeUnit.SECONDS).isEmpty());
            Thread.sleep(200L);
            assertEquals(1, fixture.requests.get());
            checkUntil(checker, GitHubReleaseChecker.Status.FAILED);
            assertEquals(1, fixture.logs.size());
            fixture.status.set(302);
            assertEquals(GitHubReleaseChecker.Status.UPDATE, checkUntil(checker, GitHubReleaseChecker.Status.UPDATE).status());
            int requests = fixture.requests.get();
            Thread.sleep(200L);
            assertEquals(requests, fixture.requests.get());
        }
    }

    private static GitHubReleaseChecker.Report checkUntil(GitHubReleaseChecker checker, GitHubReleaseChecker.Status status)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        GitHubReleaseChecker.Report report = checker.checkReport(true).get(3, TimeUnit.SECONDS);
        while ((report.status() != status || report.checkedMillis() == 0L) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
            report = checker.checkReport(true).get(3, TimeUnit.SECONDS);
        }
        return report;
    }

    private static void assertNoRelease(int status, String location) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.status.set(status);
            fixture.location.set(location);
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertEquals(GitHubReleaseChecker.Status.NO_RELEASE, checker.checkReport(false).get(3, TimeUnit.SECONDS).status());
                assertTrue(checker.check().get().isEmpty());
                assertTrue(fixture.logs.isEmpty());
            }
        }
    }

    private static void assertCurrent(String location) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.location.set(location);
            try (GitHubReleaseChecker checker = fixture.checker()) {
                checker.setEnabled(true);
                assertEquals(GitHubReleaseChecker.Status.CURRENT, checker.checkReport(false).get(3, TimeUnit.SECONDS).status());
                assertTrue(checker.check().get().isEmpty());
                assertTrue(fixture.logs.isEmpty());
            }
        }
    }

    private static String tagUrl(String tag) {
        return "https://github.com/VolmitSoftware/ShapedPortals/releases/tag/" + tag;
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger status = new AtomicInteger(302);
        private final AtomicReference<String> location = new AtomicReference<>(tagUrl("v2.0.1"));
        private final AtomicReference<String> retryAfter = new AtomicReference<>();
        private final AtomicReference<String> path = new AtomicReference<>();
        private final AtomicReference<String> method = new AtomicReference<>();
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

        private GitHubReleaseChecker checker(Duration cooldown) {
            return checker("2.0.0-1.20.1-26.2", cooldown);
        }

        private GitHubReleaseChecker checker(String installed, Duration cooldown) {
            GitHubReleaseChecker.Options options = new GitHubReleaseChecker.Options("VolmitSoftware", "ShapedPortals", installed, logger);
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort() + "/VolmitSoftware/ShapedPortals/releases/latest");
            return new GitHubReleaseChecker(options, new GitHubReleaseChecker.Access(endpoint, cooldown));
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
            method.set(exchange.getRequestMethod());
            int responseStatus = status.get();
            String target = location.get();
            String retry = retryAfter.get();
            entered.countDown();
            try {
                if (!blocked.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Release fixture timed out");
                }
                if (target != null) {
                    exchange.getResponseHeaders().set("Location", target);
                }
                if (retry != null) {
                    exchange.getResponseHeaders().set("Retry-After", retry);
                }
                exchange.sendResponseHeaders(responseStatus, -1);
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
