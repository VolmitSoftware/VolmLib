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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RemoteLanguageCatalogTest {
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void downloadsValidatesAndReusesExactCachedBytes() throws Exception {
        byte[] content = "messages:\n  runtime:\n    prefix: '&6Test'\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(content);
        try (URLClassLoader resources = resources(hash(content));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            RemoteLanguageCatalog.RequestState state = catalog.request("fr_FR", (locale, raw) -> {
                if (!raw.contains("runtime")) {
                    throw new IOException("Missing runtime section");
                }
            }, value -> {
                result.set(value);
                completed.countDown();
            });

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED, state);
            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertNotNull(result.get());
            assertTrue(result.get().successful());
            assertArrayEquals(content, Files.readAllBytes(catalog.cacheFile("fr_FR")));

            RemoteLanguageCatalog.CacheResult cached = catalog.read("fr_FR", (locale, raw) -> {
            });
            assertEquals(RemoteLanguageCatalog.CacheState.VALID, cached.state());
            assertEquals(new String(content, StandardCharsets.UTF_8), cached.content());
            assertEquals(RemoteLanguageCatalog.RequestState.CURRENT,
                    catalog.request("fr_FR", (locale, raw) -> {
                    }, ignored -> {
                    }));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void installsVerifiedBytesDirectlyWithoutCreatingARevisionCache() throws Exception {
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
            assertFalse(Files.exists(catalog.cacheFile("fr_FR")));
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
    public void checksumFailurePreservesExistingCache() throws Exception {
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        byte[] response = "different".getBytes(StandardCharsets.UTF_8);
        HttpServer server = server(response);
        try (URLClassLoader resources = resources(hash(expected));
             RemoteLanguageCatalog catalog = catalog(server, resources)) {
            Path target = catalog.cacheFile("fr_FR");
            Files.createDirectories(target.getParent());
            byte[] previous = "previous".getBytes(StandardCharsets.UTF_8);
            Files.write(target, previous);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            catalog.request("fr_FR", (locale, raw) -> {
            }, value -> {
                result.set(value);
                completed.countDown();
            });

            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertFalse(result.get().successful());
            assertTrue(result.get().failure().getMessage().contains("checksum"));
            assertArrayEquals(previous, Files.readAllBytes(target));
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
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<RemoteLanguageCatalog.DownloadResult> result = new AtomicReference<>();

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.request("fr_FR", (locale, raw) -> {
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
                    catalog.request("fr_FR", (locale, raw) -> {
                    }, ignored -> {
                    }));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void coalescesConcurrentRequestsAndCompletesEveryListener() throws Exception {
        byte[] content = "verified".getBytes(StandardCharsets.UTF_8);
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch releaseResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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
            CountDownLatch completed = new CountDownLatch(2);
            AtomicInteger completions = new AtomicInteger();
            Consumer<RemoteLanguageCatalog.DownloadResult> completion = result -> {
                if (result.successful()) {
                    completions.incrementAndGet();
                }
                completed.countDown();
            };

            assertEquals(RemoteLanguageCatalog.RequestState.SCHEDULED,
                    catalog.request("fr_FR", (locale, raw) -> {
                    }, completion));
            assertTrue(requestStarted.await(5L, TimeUnit.SECONDS));
            assertEquals(RemoteLanguageCatalog.RequestState.IN_FLIGHT,
                    catalog.request("fr_FR", (locale, raw) -> {
                    }, completion));

            releaseResponse.countDown();
            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            assertEquals(2, completions.get());
        } finally {
            releaseResponse.countDown();
            server.stop(0);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsManifestWithoutPinnedRevision() throws Exception {
        Path resources = temporaryFolder.newFolder("invalid-resources").toPath();
        Files.writeString(resources.resolve("source.properties"), "revision=main\nlocales=fr_FR\nsha256.fr_FR="
                + "0".repeat(64) + "\n");
        URL[] urls = {resources.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                    "Test",
                    URI.create("https://raw.githubusercontent.com/VolmitSoftware/Test/"),
                    "languages",
                    ".yml",
                    "source.properties",
                    temporaryFolder.newFolder("invalid-cache").toPath(),
                    loader
            ));
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
        Path resources = temporaryFolder.newFolder("resources-" + System.nanoTime()).toPath();
        Files.writeString(resources.resolve("source.properties"), "revision=" + REVISION
                + "\nlocales=fr_FR\nsha256.fr_FR=" + hash + "\n");
        return new URLClassLoader(new URL[]{resources.toUri().toURL()}, null);
    }

    private RemoteLanguageCatalog catalog(HttpServer server, ClassLoader resources) throws IOException {
        URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return RemoteLanguageCatalog.load(new RemoteLanguageCatalog.Options(
                "Test",
                root,
                "languages",
                ".yml",
                "source.properties",
                temporaryFolder.newFolder("cache-" + System.nanoTime()).toPath(),
                resources
        ));
    }

    private String hash(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        return HexFormat.of().formatHex(digest);
    }
}
