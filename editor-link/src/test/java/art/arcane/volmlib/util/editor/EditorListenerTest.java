package art.arcane.volmlib.util.editor;

import static art.arcane.volmlib.util.editor.EditorFixtures.ANIMUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.volmlib.util.editor.transport.EditorEventSink;
import art.arcane.volmlib.util.editor.transport.EditorEventStream;
import art.arcane.volmlib.util.editor.transport.EditorRequest;
import art.arcane.volmlib.util.editor.transport.EditorRequestHandler;
import art.arcane.volmlib.util.editor.transport.EditorResponse;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The direct listener over real loopback sockets, with a handler that echoes each request back as JSON. */
class EditorListenerTest {
    private static final String TOKEN = "secret-token";
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

    @TempDir Path directory;
    private final EchoHandler handler = new EchoHandler();
    private EditorListener listener;

    @AfterEach
    void stop() {
        if (listener != null) listener.close();
    }

    private static ListenerSettings plain(List<String> trustedProxies) {
        return new ListenerSettings("127.0.0.1", 0, "", "", 64, trustedProxies, 16384, "/v1/upload", 65536);
    }

    private void start(ListenerSettings settings) throws IOException {
        listener = new EditorListener(ANIMUS, settings, directory, handler, (String line) -> {});
        listener.start();
    }

    private record Reply(int status, String head, Map<String, String> headers, String body) {
        JsonObject json() throws IOException { return StrictJson.object(body); }

        String error() throws IOException { return json().getAsJsonObject("error").get("code").getAsString(); }

        String peer() throws IOException { return json().getAsJsonObject("headers").get("X-Animus-Peer").getAsString(); }
    }

    /** Sends one request on its own connection, which the server closes after answering. */
    private Reply send(String method, String target, String headers, byte[] body) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", listener.port())) {
            socket.setSoTimeout(10_000);
            OutputStream output = socket.getOutputStream();
            output.write((method + " " + target + " HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n" + headers + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            output.write(body);
            output.flush();
            byte[] reply = socket.getInputStream().readAllBytes();
            String text = new String(reply, StandardCharsets.ISO_8859_1);
            int end = text.indexOf("\r\n\r\n");
            assertTrue(end > 0, "a whole response head: " + text);
            String head = text.substring(0, end);
            String[] lines = head.split("\r\n");
            Map<String, String> fields = new LinkedHashMap<>();
            for (int index = 1; index < lines.length; index++) {
                int colon = lines[index].indexOf(':');
                fields.merge(lines[index].substring(0, colon).toLowerCase(Locale.ROOT), lines[index].substring(colon + 1).strip(), (String first, String second) -> first + ", " + second);
            }
            return new Reply(Integer.parseInt(lines[0].split(" ")[1]), head, fields, new String(reply, end + 4, reply.length - end - 4, StandardCharsets.UTF_8));
        }
    }

    private Reply get(String target, String headers) throws IOException { return send("GET", target, headers, new byte[0]); }

    private Reply post(String target, String headers, byte[] body) throws IOException {
        return send("POST", target, headers + "Content-Length: " + body.length + "\r\n", body);
    }

    private static byte[] json(int bytes) {
        return ("{\"pad\":\"" + "x".repeat(bytes - 10) + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void start_plainHttpOffLoopback_refused() throws IOException {
        try (EditorListener open = new EditorListener(ANIMUS, new ListenerSettings("0.0.0.0", 0, "", "", 64, List.of(), 16384, "", 0), directory, handler, (String line) -> {})) {
            IOException refused = assertThrows(IOException.class, open::start);
            assertTrue(refused.getMessage().contains("plain HTTP only on loopback"), refused.getMessage());
            assertEquals(-1, open.port());
        }
        start(plain(List.of()));
        assertTrue(listener.describe().contains("(http)"), listener.describe());
    }

    @Test
    void get_v1_reachesTheHandlerWithThePeerFromTheSocket() throws Exception {
        start(plain(List.of()));
        Reply reply = get("/animus/v1/x?y=1", "X-Animus-Peer: 6.6.6.6\r\nx-animus-peer: 6.6.6.7\r\nX-Custom: a\r\n");
        assertEquals(200, reply.status(), reply.body());
        JsonObject echo = reply.json();
        assertEquals("GET", echo.get("method").getAsString());
        assertEquals("/v1/x?y=1", echo.get("path").getAsString());
        assertEquals("DIRECT", echo.get("transport").getAsString());
        assertEquals(JsonNull.INSTANCE, echo.get("body"));
        assertEquals("127.0.0.1", reply.peer());
        assertEquals("a", echo.getAsJsonObject("headers").get("x-custom").getAsString());
        EditorRequest request = handler.handled.poll(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals(Optional.of("127.0.0.1"), request.header("x-animus-peer"));
        assertEquals("/v1/x", request.route());
    }

    @Test
    void get_v2_answersUnsupportedProtocol() throws Exception {
        start(plain(List.of()));
        for (String target : List.of("/animus/v2/x", "/animus/v1x/y", "/animus/v")) {
            Reply reply = get(target, "");
            assertEquals(400, reply.status(), target);
            assertEquals("unsupported-protocol", reply.error(), target);
        }
        assertNull(handler.handled.poll());
    }

    @Test
    void get_outsideTheBase_answersNotFound() throws Exception {
        start(plain(List.of()));
        for (String target : List.of("/other", "/animus", "/animus/", "/animusx/v1/x", "/gloss/v1/x")) {
            Reply reply = get(target, "");
            assertEquals(404, reply.status(), target);
            assertEquals("not-found", reply.error(), target);
        }
        assertNull(handler.handled.poll());
    }

    @Test
    void head_malformedOrDuplicated_answersInvalidRequest() throws Exception {
        start(plain(List.of()));
        assertEquals("invalid-request", get("/animus/v1/x", "Content-Length: 1\r\nContent-Length: 1\r\n").error());
        assertEquals("invalid-request", get("/animus/v1/x", "Authorization: Bearer a\r\nAuthorization: Bearer b\r\n").error());
        assertEquals("invalid-request", get("/animus/v1/x", "Content-Length: many\r\n").error());
        assertEquals("invalid-request", get("/animus/v1/x", "no colon here\r\n").error());
        assertEquals("invalid-request", get("animus/v1/x", "").error());
        assertNull(handler.handled.poll());
    }

    @Test
    void post_chunked_refused() throws Exception {
        start(plain(List.of()));
        Reply reply = send("POST", "/animus/v1/x", "Transfer-Encoding: chunked\r\n", new byte[0]);
        assertEquals(400, reply.status());
        assertEquals("invalid-request", reply.error());
        assertNull(handler.handled.poll());
    }

    @Test
    void post_bodyOverOpenLimit_refused413UnlessAuthenticated() throws Exception {
        start(plain(List.of()));
        Reply open = send("POST", "/animus/v1/pair", "Content-Length: " + (EditorListener.OPEN_BODY_BYTES + 1) + "\r\n", new byte[0]);
        assertEquals(413, open.status());
        assertEquals("too-large", open.error());
        assertTrue(open.body().contains("limited to 4096 bytes"), open.body());
        assertEquals(200, post("/animus/v1/pair", "", json(EditorListener.OPEN_BODY_BYTES)).status());
        Reply authenticated = post("/animus/v1/x", "Authorization: Bearer " + TOKEN + "\r\n", json(EditorListener.OPEN_BODY_BYTES + 1000));
        assertEquals(200, authenticated.status(), authenticated.body());
        assertEquals(EditorListener.OPEN_BODY_BYTES + 1000 - 10, authenticated.json().getAsJsonObject("body").get("pad").getAsString().length());
        Reply over = send("POST", "/animus/v1/x", "Authorization: Bearer " + TOKEN + "\r\nContent-Length: 16385\r\n", new byte[0]);
        assertEquals(413, over.status());
    }

    @Test
    void post_duplicateKeyBody_answersInvalidRequest() throws Exception {
        start(plain(List.of()));
        Reply duplicate = post("/animus/v1/x", "Content-Type: application/json\r\n", "{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, duplicate.status());
        assertEquals("invalid-request", duplicate.error());
        assertTrue(duplicate.json().getAsJsonObject("error").get("message").getAsString().contains("duplicate key"), duplicate.body());
        assertEquals("invalid-request", post("/animus/v1/x", "", "{a:1}".getBytes(StandardCharsets.UTF_8)).error());
        assertEquals("invalid-request", post("/animus/v1/x", "", "[1]".getBytes(StandardCharsets.UTF_8)).error());
        assertNull(handler.handled.poll());
    }

    @Test
    void post_uploadRoute_passesRawBytesAsBase64WithTheLargerLimit() throws Exception {
        start(plain(List.of()));
        byte[] raw = new byte[20_000];
        new Random(7).nextBytes(raw);
        Reply upload = post("/animus/v1/upload?name=a.bin", "Authorization: Bearer " + TOKEN + "\r\n", raw);
        assertEquals(200, upload.status(), upload.body());
        assertEquals(Base64.getEncoder().encodeToString(raw), upload.json().get("body").getAsString());
        assertEquals(413, send("POST", "/animus/v1/upload", "Content-Length: 5000\r\n", new byte[0]).status());
        assertEquals(413, send("POST", "/animus/v1/upload", "Authorization: Bearer " + TOKEN + "\r\nContent-Length: 65537\r\n", new byte[0]).status());
        assertEquals(413, send("POST", "/animus/v1/x", "Authorization: Bearer " + TOKEN + "\r\nContent-Length: 20000\r\n", new byte[0]).status());
    }

    @Test
    void forwardedFor_trustedProxy_becomesThePeer() throws Exception {
        start(plain(List.of()));
        assertEquals("127.0.0.1", get("/animus/v1/x", "X-Forwarded-For: 203.0.113.9\r\n").peer());
        listener.settings(plain(List.of("127.0.0.1")));
        assertEquals("203.0.113.9", get("/animus/v1/x", "X-Forwarded-For: 203.0.113.9\r\n").peer());
        assertEquals("203.0.113.9", get("/animus/v1/x", "X-Forwarded-For: 198.51.100.1, 203.0.113.9\r\n").peer());
        assertEquals("203.0.113.9", get("/animus/v1/x", "X-Forwarded-For: 203.0.113.9, 127.0.0.1\r\n").peer());
        assertEquals("127.0.0.1", get("/animus/v1/x", "X-Forwarded-For: client.example\r\n").peer());
        assertEquals("127.0.0.1", get("/animus/v1/x", "").peer());
    }

    @Test
    void response_cacheControl_noStoreUnlessTheHandlerSetsOne() throws Exception {
        start(plain(List.of()));
        Reply plain = get("/animus/v1/x", "");
        assertEquals("no-store", plain.headers().get("cache-control"));
        assertEquals("nosniff", plain.headers().get("x-content-type-options"));
        assertEquals("no-referrer", plain.headers().get("referrer-policy"));
        assertEquals("application/json; charset=utf-8", plain.headers().get("content-type"));
        assertEquals("no-store", get("/other", "").headers().get("cache-control"));
        Reply cached = get("/animus/v1/cached", "");
        assertEquals("max-age=60", cached.headers().get("cache-control"));
        assertFalse(cached.head().contains("no-store"), cached.head());
    }

    @Test
    void websocket_authFrame_thenEvents() throws Exception {
        start(plain(List.of()));
        EventSocket socket = socket();
        socket.socket().sendText("{\"type\":\"auth\",\"token\":\"" + TOKEN + "\"}", true).get(5, TimeUnit.SECONDS);
        EditorEventSink sink = handler.sinks.poll(5, TimeUnit.SECONDS);
        assertNotNull(sink, "the stream opened");
        EditorRequest opened = handler.opened;
        assertEquals("/v1/events", opened.path());
        assertEquals(Optional.of("Bearer " + TOKEN), opened.header("authorization"));
        assertEquals(Optional.of("127.0.0.1"), opened.header("X-Animus-Peer"));
        JsonObject event = new JsonObject();
        event.addProperty("type", "change");
        event.add("item", JsonNull.INSTANCE);
        assertTrue(sink.send(event));
        assertEquals("{\"type\":\"change\",\"item\":null}", socket.frames().poll(5, TimeUnit.SECONDS));
        sink.close();
        assertEquals(1000, socket.closed().get(5, TimeUnit.SECONDS));
        assertTrue(handler.streamClosed.await(5, TimeUnit.SECONDS), "the stream's close ran");
        assertFalse(sink.send(event));
    }

    @Test
    void websocket_badAuthFrame_closes4400() throws Exception {
        start(plain(List.of()));
        for (String frame : List.of("not json", "{\"type\":\"auth\"}", "{\"type\":\"hello\",\"token\":\"x\"}", "{\"type\":\"auth\",\"token\":\"x\",\"token\":\"y\"}")) {
            EventSocket socket = socket();
            socket.socket().sendText(frame, true).get(5, TimeUnit.SECONDS);
            assertEquals(4400, socket.closed().get(5, TimeUnit.SECONDS), frame);
        }
        EventSocket refused = socket();
        refused.socket().sendText("{\"type\":\"auth\",\"token\":\"wrong\"}", true).get(5, TimeUnit.SECONDS);
        assertEquals(4401, refused.closed().get(5, TimeUnit.SECONDS));
        assertNull(handler.sinks.poll());
    }

    private record EventSocket(WebSocket socket, BlockingQueue<String> frames, CompletableFuture<Integer> closed) {}

    private EventSocket socket() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        WebSocket socket = HTTP.newWebSocketBuilder().buildAsync(URI.create("ws://127.0.0.1:" + listener.port() + "/animus/v1/events"), new WebSocket.Listener() {
            private final StringBuilder text = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                text.append(data);
                if (last) {
                    frames.add(text.toString());
                    text.setLength(0);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closed.complete(statusCode);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) { closed.completeExceptionally(error); }
        }).get(5, TimeUnit.SECONDS);
        return new EventSocket(socket, frames, closed);
    }

    /** TLS settings from a self-signed EC certificate made with keytool and written as PEM into the data folder. */
    private ListenerSettings tls() throws Exception {
        Path store = directory.resolve("tls.p12");
        char[] password = "changeit".toCharArray();
        Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "editor", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", "changeit", "-keypass", "changeit").redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(keytool.waitFor(60, TimeUnit.SECONDS) && keytool.exitValue() == 0, output);
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) { keys.load(input, password); }
        Base64.Encoder pem = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
        Files.writeString(directory.resolve("certificate.pem"),
                "-----BEGIN CERTIFICATE-----\n" + pem.encodeToString(keys.getCertificate("editor").getEncoded()) + "\n-----END CERTIFICATE-----\n");
        Files.writeString(directory.resolve("key.pem"), "-----BEGIN PRIVATE KEY-----\n" + pem.encodeToString(keys.getKey("editor", password).getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        return new ListenerSettings("127.0.0.1", 0, "certificate.pem", "key.pem", 64, List.of(), 16384, "", 0);
    }

    @Test
    void tls_pemFiles_serveHttps() throws Exception {
        start(tls());
        assertTrue(listener.describe().contains("(https)"), listener.describe());
        SSLContext trustAll = SSLContext.getInstance("TLS");
        trustAll.init(null, new TrustManager[]{new TrustAll()}, null);
        HttpClient client = HttpClient.newBuilder().sslContext(trustAll).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> reply = client.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + listener.port() + "/animus/v1/x")).timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reply.statusCode(), reply.body());
        assertEquals("127.0.0.1", StrictJson.object(reply.body()).getAsJsonObject("headers").get("X-Animus-Peer").getAsString());
        assertThrows(IOException.class, () -> HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listener.port() + "/animus/v1/x"))
                .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void tls_trickledHandshake_cutOffWithinItsDeadline() throws Exception {
        start(tls());
        byte[] hello = new byte[64];
        System.arraycopy(new byte[]{0x16, 0x03, 0x01, 0x02, 0x00, 0x01, 0x00, 0x01, (byte) 0xfc, 0x03, 0x03}, 0, hello, 0, 11);
        try (Socket client = new Socket("127.0.0.1", listener.port())) {
            long started = System.nanoTime();
            assertTrue(trickle(client, hello, 12), "a handshake that trickles in under the read timeout is dropped within its deadline");
            assertTrue(System.nanoTime() - started < TimeUnit.SECONDS.toNanos(12));
        }
    }

    /** Sends one byte about every second; true once the server answers or closes, false when it still waits after {@code seconds}. */
    private static boolean trickle(Socket client, byte[] bytes, int seconds) throws IOException {
        InputStream input = client.getInputStream();
        OutputStream output = client.getOutputStream();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        for (byte next : bytes) {
            long left = (deadline - System.nanoTime()) / 1_000_000;
            if (left <= 0) return false;
            try {
                output.write(next);
                output.flush();
                client.setSoTimeout((int) Math.min(1000, left));
                input.read();
                return true;
            } catch (SocketTimeoutException waiting) {
                // Still waiting for the rest of the hello.
            } catch (IOException reset) {
                return true;
            }
        }
        return false;
    }

    private static final class TrustAll extends X509ExtendedTrustManager {
        @Override public void checkClientTrusted(X509Certificate[] chain, String type) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String type) {}
        @Override public void checkClientTrusted(X509Certificate[] chain, String type, Socket socket) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String type, Socket socket) {}
        @Override public void checkClientTrusted(X509Certificate[] chain, String type, SSLEngine engine) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String type, SSLEngine engine) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }

    /** Echoes each request as JSON; a request with the test token is authenticated, and only it may open an event stream. */
    private static final class EchoHandler implements EditorRequestHandler {
        final BlockingQueue<EditorRequest> handled = new LinkedBlockingQueue<>();
        final BlockingQueue<EditorEventSink> sinks = new LinkedBlockingQueue<>();
        final CountDownLatch streamClosed = new CountDownLatch(1);
        volatile EditorRequest opened;

        @Override
        public Admission admit(EditorRequest head) {
            return head.header("Authorization").equals(Optional.of("Bearer " + TOKEN)) ? Admission.AUTHENTICATED : Admission.OPEN;
        }

        @Override
        public CompletionStage<EditorResponse> handle(EditorRequest request) {
            handled.add(request);
            JsonObject echo = new JsonObject();
            echo.addProperty("method", request.method());
            echo.addProperty("path", request.path());
            echo.addProperty("transport", request.transport().name());
            JsonObject headers = new JsonObject();
            request.headers().forEach(headers::addProperty);
            echo.add("headers", headers);
            echo.add("body", request.body().orElse(JsonNull.INSTANCE));
            Map<String, String> extra = request.route().equals("/v1/cached") ? Map.of("Cache-Control", "max-age=60") : Map.of();
            return CompletableFuture.completedFuture(new EditorResponse(200, extra, Optional.of(echo)));
        }

        @Override
        public CompletionStage<EditorEventStream> openEvents(EditorRequest request, EditorEventSink sink) {
            opened = request;
            if (!request.header("Authorization").equals(Optional.of("Bearer " + TOKEN)))
                return CompletableFuture.completedFuture(EditorEventStream.refused(EditorResponse.error(401, "unauthorized", "A valid editor token is required.")));
            sinks.add(sink);
            return CompletableFuture.completedFuture(new EditorEventStream(EditorResponse.json(200, new JsonObject()), streamClosed::countDown));
        }
    }
}
