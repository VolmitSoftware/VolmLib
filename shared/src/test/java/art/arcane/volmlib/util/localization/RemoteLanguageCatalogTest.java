package art.arcane.volmlib.util.localization;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class RemoteLanguageCatalogTest {
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installsPartialCatalogsWithInvalidEntriesWithoutLosingValidTranslations() throws Exception {
        String source = "[message]\ngreeting = \"Bonjour {name}\"\ninvalid = 42\n";
        byte[] content = source.getBytes(StandardCharsets.UTF_8);
        MessageCatalog messages = MessageCatalog.of("en_US",
                TextKey.of("message.greeting", "Hello {name}"),
                TextKey.of("message.invalid", "English"),
                TextKey.of("message.missing", "Default"));
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("partial-language").toPath().resolve("fr_FR.toml");
            assertEquals(source, catalog.readOrInstall("fr_FR", target,
                    (locale, raw) -> TomlLanguageParser.parseValidText(raw, messages)));
            assertArrayEquals(content, Files.readAllBytes(target));
            assertEquals(Map.of("message.greeting", "Bonjour {name}"),
                    TomlLanguageParser.parseValidText(Files.readString(target), messages));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void installsVerifiedBytesDirectly() throws Exception {
        byte[] content = "[runtime]\nprefix = \"&6Test\"\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("languages").toPath().resolve("fr_FR.toml");
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            RemoteLanguageCatalog.RequestState state = catalog.requestInstallIfMissing(
                    "fr_FR", target, (locale, raw) -> {
                        if (!raw.contains("prefix")) {
                            throw new IOException("Missing prefix");
                        }
                    }, value -> {
                        result.set(value);
                        completed.countDown();
                    });

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED, state);
            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertTrue(result.get().successful());
            assertEquals(target.toAbsolutePath().normalize(), result.get().file());
            assertArrayEquals(content, Files.readAllBytes(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void directInstallNeverReplacesAnExistingEditableFile() throws Exception {
        byte[] content = "downloaded".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("existing-language").toPath().resolve("fr_FR.toml");
            byte[] local = "operator edit".getBytes(StandardCharsets.UTF_8);
            Files.write(target, local);

            RemoteLanguageCatalog.RequestState state = catalog.requestInstallIfMissing(
                    "fr_FR", target, (locale, raw) -> {
                    }, ignored -> {
                    });

            assertEquals(RemoteLanguageCatalog.RequestState.CURRENT, state);
            assertArrayEquals(local, Files.readAllBytes(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void blockingInstallPreservesEditedLanguageWhenOffline() throws Exception {
        byte[] content = "downloaded language".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources("main", null);
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("blocking-language").toPath().resolve("fr_FR.yml");
            AtomicInteger validations = new AtomicInteger();

            assertEquals("downloaded language", catalog.readOrInstall("fr_FR", target, (locale, raw) -> {
                assertEquals("fr_FR", locale);
                assertEquals("downloaded language", raw);
                validations.incrementAndGet();
            }));
            assertArrayEquals(content, Files.readAllBytes(target));
            assertTrue(validations.get() > 0);

            Files.writeString(target, "operator edit");
            server.stop(0);

            assertEquals("operator edit", catalog.readOrInstall("fr_FR", target,
                    (locale, raw) -> assertEquals("operator edit", raw)));
            assertThrows(IOException.class, () -> catalog.readOrInstall("fr_FR", target, (locale, raw) -> {
                throw new IOException("Invalid translation");
            }));
            assertEquals("operator edit", Files.readString(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void asynchronousInstallPreservesEditedLanguageWhenOffline() throws Exception {
        byte[] content = "verified language".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("asynchronous-language").toPath().resolve("fr_FR.yml");
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                        assertEquals("fr_FR", locale);
                        assertEquals("verified language", raw);
                    }, value -> {
                        result.set(value);
                        completed.countDown();
                    }));

            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertTrue(result.get().successful());
            assertEquals(target.toAbsolutePath().normalize(), result.get().file());
            assertArrayEquals(content, Files.readAllBytes(target));

            Files.writeString(target, "operator edit");
            server.stop(0);

            assertEquals(RemoteLanguageCatalog.RequestState.CURRENT,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {}, ignored -> {}));
            assertEquals("operator edit", catalog.readOrInstall("fr_FR", target,
                    (locale, raw) -> assertEquals("operator edit", raw)));
            assertEquals("operator edit", Files.readString(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void blockingInstallPreservesAFileCreatedDuringDownload() throws Exception {
        byte[] content = "downloaded language".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("concurrent-language").toPath().resolve("fr_FR.yml");

            assertEquals("operator edit", catalog.readOrInstall("fr_FR", target, (locale, raw) -> {
                if (raw.equals("downloaded language")) {
                    Files.writeString(target, "operator edit");
                }
            }));
            assertEquals("operator edit", Files.readString(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void blockingInstallRejectsMismatchedDownloadBeforePublication() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server("different".getBytes(StandardCharsets.UTF_8));
        try (URLClassLoader resources = resources(hash(expected));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("invalid-language").toPath().resolve("fr_FR.yml");

            assertThrows(IllegalArgumentException.class,
                    () -> catalog.readOrInstall("fr_FR", target, (locale, raw) -> {}));
            assertFalse(Files.exists(target));
            assertThrows(IOException.class,
                    () -> catalog.readOrInstall("fr_FR", target, (locale, raw) -> {}));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void checksumFailureRejectsDownloadBeforeInstallation() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        byte[] response = "different".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response);
        try (URLClassLoader resources = resources(hash(expected));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("checksum-language").toPath().resolve("fr_FR.yml");
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
            }, value -> {
                result.set(value);
                completed.countDown();
            });

            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertFalse(result.get().successful());
            assertTrue(result.get().failure().getMessage().contains("checksum"));
            assertFalse(Files.exists(target));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void reportsLocaleAndSourceUrlAndSuppressesImmediateRetryAfterHttpFailure() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1L);
            exchange.close();
        });
        server.start();
        try (URLClassLoader resources = resources(hash(expected));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("requested-language").toPath().resolve("fr_FR.yml");
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                    }, value -> {
                        result.set(value);
                        completed.countDown();
                    }));

            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertFalse(result.get().successful());
            assertTrue(result.get().failure().getMessage().contains("Unable to fetch language file fr_FR from "));
            assertTrue(result.get().failure().getMessage().contains(result.get().source().toString()));
            assertTrue(result.get().failure().getMessage().contains("HTTP 404"));
            assertEquals(RemoteLanguageCatalog.RequestState.COOLDOWN,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                    }, ignored -> {
                    }));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void coalescesConcurrentRequestsAndCompletesEveryListener() throws Exception {
        byte[] content = "verified".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestStarted.countDown();
            try {
                releaseResponse.await(5L, TimeUnit.SECONDS);
                respond(exchange, content);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("requested-language").toPath().resolve("fr_FR.yml");
            CountDownLatch completed = new CountDownLatch(2);
            AtomicInteger completions = new AtomicInteger();
            Consumer<RemoteLanguageCatalog.DownloadResult> completion = result -> {
                if (result.successful()) {
                    completions.incrementAndGet();
                }
                completed.countDown();
            };

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                    }, completion));
            assertTrue(requestStarted.await(5L, TimeUnit.SECONDS));
            assertEquals(RemoteLanguageCatalog.RequestState.IN_FLIGHT,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                    }, completion));

            releaseResponse.countDown();
            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertEquals(2, completions.get());
            assertEquals(1, requests.get());
            assertArrayEquals(content, Files.readAllBytes(target));
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    @Test
    public void downloadsFromMutableReferenceWithoutChecksum() throws Exception {
        byte[] content = "[runtime]\nprefix = \"&6Latest\"\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources("main", null);
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("mutable-language").toPath().resolve("fr_FR.toml");
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            assertEquals("main", catalog.revision());
            assertEquals("main/languages/fr_FR.yml", catalog.sourceUri("fr_FR").getPath().substring(1));
            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.requestInstallIfMissing("fr_FR", target, (locale, raw) -> {
                    }, value -> {
                        result.set(value);
                        completed.countDown();
                    }));

            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertTrue(result.get().successful());
            assertArrayEquals(content, Files.readAllBytes(target));
        } finally {
            server.stop(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsInvalidSourceReference() throws Exception {
        Path resources = temporaryFolder.newFolder("invalid-resources").toPath();
        Files.writeString(resources.resolve("source.properties"), "revision=../main\nlocales=fr_FR\n");
        URL[] urls = {resources.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                    "Test",
                    URI.create("https://raw.githubusercontent.com/VolmitSoftware/Test/"),
                    "languages",
                    ".yml",
                    "source.properties",
                    loader
            ));
        }
    }

    @Test
    public void closingRejectsABlockingDirectInstallAfterValidation() throws Exception {
        assertCloseRejectsPublication(false);
    }

    @Test
    public void closingRejectsAnAsynchronousDirectInstallAfterValidation() throws Exception {
        assertCloseRejectsPublication(true);
    }

    private void assertCloseRejectsPublication(boolean asynchronous) throws Exception {
        byte[] content = "downloaded language".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        AtomicReference<Thread> validationThread = new AtomicReference<>();
        AtomicInteger callbacks = new AtomicInteger();
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = temporaryFolder.newFolder("closing-install").toPath().resolve("fr_FR.toml");
            RemoteLanguageCatalog.ContentValidator validator = (locale, raw) -> {
                validationThread.set(Thread.currentThread());
                entered.countDown();
                awaitRelease(released);
            };
            Future<String> blocking = null;
            if (asynchronous) {
                assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                        catalog.requestInstallIfMissing("fr_FR", target, validator, result -> callbacks.incrementAndGet()));
            } else {
                blocking = workers.submit(() -> catalog.readOrInstall("fr_FR", target, validator));
            }
            assertTrue(entered.await(2L, TimeUnit.SECONDS));
            Future<?> closed = workers.submit(catalog::close);
            closed.get(1L, TimeUnit.SECONDS);
            released.countDown();
            if (asynchronous) {
                validationThread.get().join(2_000L);
                assertFalse(validationThread.get().isAlive());
            } else {
                Future<String> download = blocking;
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> download.get(2L, TimeUnit.SECONDS));
                assertTrue(failure.getCause() instanceof IOException);
            }
            assertEquals(0, callbacks.get());
            assertFalse(Files.exists(target));
            assertEquals(RemoteLanguageCatalog.RequestState.CLOSED,
                    catalog.requestInstallIfMissing("fr_FR", target, validator, result -> callbacks.incrementAndGet()));
        } finally {
            released.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(2L, TimeUnit.SECONDS));
            server.stop(0);
        }
    }

    private void awaitRelease(CountDownLatch released) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
        while (released.getCount() != 0L) {
            try {
                assertTrue(released.await(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            } catch (InterruptedException interrupted) {
                assertTrue(System.nanoTime() < deadline);
            }
        }
    }

    private HttpServer server(byte[] response) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, response));
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, byte[] response) throws IOException {
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private URLClassLoader resources(String hash) throws Exception {
        return resources(REVISION, hash);
    }

    private URLClassLoader resources(String revision, String hash) throws Exception {
        Path resources = temporaryFolder.newFolder("resources-" + System.nanoTime()).toPath();
        String checksum = hash == null ? "" : "sha256.fr_FR=" + hash + "\n";
        Files.writeString(resources.resolve("source.properties"), "revision=" + revision
                + "\nlocales=fr_FR\n" + checksum);
        return new URLClassLoader(new URL[]{resources.toUri().toURL()}, null);
    }

    private RemoteLanguageCatalog catalog(HttpServer server, ClassLoader resources) {
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                "Test",
                root,
                "languages",
                ".yml",
                "source.properties",
                resources
        ));
    }

    private String hash(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        return HexFormat.of().formatHex(digest);
    }
}
