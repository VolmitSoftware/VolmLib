package art.arcane.volmlib.util.editor.relay;

import static art.arcane.volmlib.util.editor.EditorFixtures.ANIMUS;
import static art.arcane.volmlib.util.editor.EditorFixtures.GLOSS;
import static art.arcane.volmlib.util.editor.EditorFixtures.verifies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.volmlib.util.editor.EditorIdentity;
import art.arcane.volmlib.util.editor.EditorLog;
import art.arcane.volmlib.util.editor.EditorProduct;
import art.arcane.volmlib.util.editor.StrictJson;
import art.arcane.volmlib.util.editor.transport.EditorEventSink;
import art.arcane.volmlib.util.editor.transport.EditorEventStream;
import art.arcane.volmlib.util.editor.transport.EditorRequest;
import art.arcane.volmlib.util.editor.transport.EditorRequestHandler;
import art.arcane.volmlib.util.editor.transport.EditorResponse;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EditorRelayClientTest {
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String NONCE = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String CHALLENGE = "BBECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private final FakeHandler handler = new FakeHandler();
    private final EditorIdentity identity = EditorIdentity.generate();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private FakeRelay relay;
    private EditorRelayClient client;

    @BeforeEach
    void startRelay() throws Exception { relay = new FakeRelay(); }

    @AfterEach
    void stop() throws Exception {
        if (client != null) client.close();
        relay.close();
    }

    private RelaySettings settings(int concurrent) { return new RelaySettings(true, relay.url(), 1, 2, 1024 * 1024, concurrent); }

    private EditorRelayClient client(EditorProduct product, EditorRequestHandler requests, RelaySettings settings, Duration idle) {
        client = new EditorRelayClient(product, settings, identity, requests, new EditorLog((String line) -> {}, (String line) -> {}, warnings::add), Duration.ofSeconds(2), idle);
        client.start();
        return client;
    }

    private FakeRelay.Session connect(EditorRequestHandler requests, RelaySettings settings, Duration idle) throws Exception {
        client(ANIMUS, requests, settings, idle);
        FakeRelay.Session session = relay.accept(WAIT);
        session.register(ANIMUS, NONCE);
        awaitRegistered();
        return session;
    }

    private FakeRelay.Session connect() throws Exception { return connect(handler, settings(64), Duration.ofSeconds(90)); }

    private void awaitRegistered() throws InterruptedException {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (!client.isRegistered() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(client.isRegistered(), "registered");
    }

    private static JsonObject request(long id, String method, String path) {
        JsonObject frame = new JsonObject();
        frame.addProperty("type", "request");
        frame.addProperty("id", id);
        frame.addProperty("method", method);
        frame.addProperty("path", path);
        JsonObject headers = new JsonObject();
        headers.addProperty("Authorization", "Bearer anm_x");
        frame.add("headers", headers);
        return frame;
    }

    private static JsonObject event(int number) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "change");
        event.addProperty("n", number);
        return event;
    }

    @Test
    void register_challenge_signedWithTheProductPrefix() throws Exception {
        for (EditorProduct product : List.of(ANIMUS, GLOSS)) {
            client(product, handler, settings(64), Duration.ofSeconds(90));
            JsonObject register = relay.accept(WAIT).register(product, NONCE);
            assertEquals("register", register.get("type").getAsString());
            assertEquals(1, register.get("protocol").getAsInt());
            assertEquals(identity.serverKey(), register.get("serverKey").getAsString());
            assertEquals(4, register.size());
            String signature = register.get("signature").getAsString();
            assertTrue(verifies(identity.serverKey(), product.relayChallengePrefix() + NONCE, signature));
            assertFalse(verifies(identity.serverKey(), (product == ANIMUS ? GLOSS : ANIMUS).relayChallengePrefix() + NONCE, signature));
            assertFalse(verifies(identity.serverKey(), product.proofPrefix() + NONCE, signature));
            awaitRegistered();
            client.close();
            client = null;
        }
    }

    @Test
    void request_frame_answeredByTheHandler() throws Exception {
        FakeRelay.Session session = connect();
        JsonObject frame = request(7, "post", "/v1/x?y=1");
        frame.getAsJsonObject("headers").addProperty("X-Animus-Counter", "3");
        frame.add("body", StrictJson.object("{\"a\":1}"));
        session.send(frame);
        FakeHandler.Call call = handler.calls.poll(5, TimeUnit.SECONDS);
        assertNotNull(call);
        EditorRequest request = call.request();
        assertEquals(EditorRequest.Transport.RELAY, request.transport());
        assertEquals("POST", request.method());
        assertEquals("/v1/x?y=1", request.path());
        assertEquals(Optional.of("Bearer anm_x"), request.header("authorization"));
        assertEquals(Optional.of("3"), request.header("x-animus-counter"));
        assertEquals(StrictJson.object("{\"a\":1}"), request.body().orElseThrow());
        call.response().complete(EditorResponse.json(200, StrictJson.object("{\"ok\":true,\"item\":null}")));
        assertEquals(StrictJson.object("{\"type\":\"response\",\"id\":7,\"status\":200,\"body\":{\"ok\":true,\"item\":null}}"), session.next(WAIT));
    }

    @Test
    void challengeHeader_signedProofAndServerKeyAdded() throws Exception {
        FakeRelay.Session session = connect();
        assertEquals("X-Animus-Challenge", client.challengeHeader());
        JsonObject hello = request(5, "GET", "/v1/hello");
        hello.getAsJsonObject("headers").addProperty("X-Animus-Challenge", CHALLENGE);
        session.send(hello);
        handler.calls.poll(5, TimeUnit.SECONDS).response().complete(EditorResponse.json(200, new JsonObject()));
        JsonObject headers = session.next(WAIT).getAsJsonObject("headers");
        assertEquals(identity.serverKey(), headers.get("X-Animus-Server-Key").getAsString());
        String proof = headers.get("X-Animus-Proof").getAsString();
        assertTrue(verifies(identity.serverKey(), "animus-editor-proof/1 " + CHALLENGE, proof));
        assertFalse(verifies(identity.serverKey(), "animus-relay/1 " + CHALLENGE, proof));
        JsonObject malformed = request(6, "GET", "/v1/hello");
        malformed.getAsJsonObject("headers").addProperty("X-Animus-Challenge", "animus-relay/1 x");
        session.send(malformed);
        handler.calls.poll(5, TimeUnit.SECONDS).response().complete(EditorResponse.json(200, new JsonObject()));
        assertFalse(session.next(WAIT).has("headers"));
    }

    @Test
    void request_blockedHandler_doesNotHoldUpTheNextRequest() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        EditorRequestHandler blocking = new EditorRequestHandler() {
            @Override
            public CompletionStage<EditorResponse> handle(EditorRequest request) {
                if (request.route().equals("/v1/slow")) {
                    try { release.await(20, TimeUnit.SECONDS); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                }
                return CompletableFuture.completedFuture(EditorResponse.json(200, new JsonObject()));
            }

            @Override
            public CompletionStage<EditorEventStream> openEvents(EditorRequest request, EditorEventSink sink) { return handler.openEvents(request, sink); }
        };
        try {
            FakeRelay.Session session = connect(blocking, settings(64), Duration.ofSeconds(90));
            session.send(request(1, "GET", "/v1/slow"));
            session.send(request(2, "GET", "/v1/fast"));
            assertEquals(2, session.next(WAIT).get("id").getAsLong(), "answered while the first request still blocks");
            release.countDown();
            assertEquals(1, session.next(WAIT).get("id").getAsLong());
        } finally { release.countDown(); }
    }

    @Test
    void frame_duplicateKey_endsTheConnection() throws Exception {
        FakeRelay.Session session = connect();
        session.sendText("{\"type\":\"request\",\"id\":1,\"id\":2,\"method\":\"GET\",\"path\":\"/v1/x\"}");
        assertTrue(session.awaitClosed(WAIT));
        assertTrue(handler.calls.isEmpty());
        relay.accept(WAIT).register(ANIMUS, NONCE);
        awaitRegistered();
    }

    @Test
    void idle_relayThatNeverPings_staysConnectedThroughClientPings() throws Exception {
        FakeRelay.Session session = connect(handler, settings(64), Duration.ofMillis(900));
        assertFalse(session.awaitClosed(Duration.ofSeconds(3)), "the client's pings keep a quiet relay connection alive past its idle timeout");
        assertTrue(client.isRegistered());
        assertTrue(relay.noConnection(Duration.ZERO), "no reconnect");
        session.send(request(1, "GET", "/v1/x"));
        handler.calls.poll(5, TimeUnit.SECONDS).response().complete(EditorResponse.json(200, new JsonObject()));
        assertEquals(1, session.next(WAIT).get("id").getAsLong());
    }

    @Test
    void requests_overTheConcurrencyBound_answerBusy() throws Exception {
        FakeRelay.Session session = connect(handler, settings(1), Duration.ofSeconds(90));
        session.send(request(1, "GET", "/v1/x"));
        FakeHandler.Call held = handler.calls.poll(5, TimeUnit.SECONDS);
        assertNotNull(held);
        session.send(request(2, "GET", "/v1/x"));
        JsonObject busy = session.next(WAIT);
        assertEquals(2, busy.get("id").getAsLong());
        assertEquals(503, busy.get("status").getAsInt());
        JsonObject error = busy.getAsJsonObject("body").getAsJsonObject("error");
        assertEquals("busy", error.get("code").getAsString());
        assertEquals(1000, error.get("retryAfterMillis").getAsLong());
        assertEquals("1", busy.getAsJsonObject("headers").get("Retry-After").getAsString());
        held.response().complete(EditorResponse.json(200, new JsonObject()));
        assertEquals(1, session.next(WAIT).get("id").getAsLong());
        session.send(request(3, "GET", "/v1/x"));
        handler.calls.poll(5, TimeUnit.SECONDS).response().complete(EditorResponse.json(200, new JsonObject()));
        assertEquals(200, session.next(WAIT).get("status").getAsInt(), "the slot is free again");
    }

    @Test
    void eventStream_eventsBeforeTheResponse_followIt() throws Exception {
        FakeRelay.Session session = connect();
        session.send(request(3, "GET", "/v1/events"));
        FakeHandler.Stream stream = handler.streams.poll(5, TimeUnit.SECONDS);
        assertNotNull(stream);
        assertTrue(stream.sink().send(event(1)));
        assertTrue(stream.sink().send(event(2)));
        CountDownLatch closed = new CountDownLatch(1);
        stream.opened().complete(new EditorEventStream(EditorResponse.json(200, new JsonObject()), closed::countDown));
        JsonObject response = session.next(WAIT);
        assertEquals("response", response.get("type").getAsString());
        assertEquals(200, response.get("status").getAsInt());
        for (int number = 1; number <= 3; number++) {
            if (number == 3) assertTrue(stream.sink().send(event(3)));
            JsonObject frame = session.next(WAIT);
            assertEquals("event", frame.get("type").getAsString());
            assertEquals(3, frame.get("id").getAsLong());
            assertEquals(event(number), frame.getAsJsonObject("event"));
        }
        stream.sink().close();
        JsonObject close = session.next(WAIT);
        assertEquals("close", close.get("type").getAsString());
        assertEquals(3, close.get("id").getAsLong());
        assertTrue(closed.await(5, TimeUnit.SECONDS));
    }

    @Test
    void eventStream_refused_takesNoEvents() throws Exception {
        FakeRelay.Session session = connect();
        session.send(request(5, "GET", "/v1/events"));
        FakeHandler.Stream stream = handler.streams.poll(5, TimeUnit.SECONDS);
        assertNotNull(stream);
        stream.opened().complete(EditorEventStream.refused(EditorResponse.error(401, "unauthorized", "no token")));
        assertEquals(401, session.next(WAIT).get("status").getAsInt());
        assertFalse(stream.sink().send(event(1)));
    }

    /** Records calls and lets each test answer them. */
    private static final class FakeHandler implements EditorRequestHandler {
        record Call(EditorRequest request, CompletableFuture<EditorResponse> response) {}

        record Stream(EditorRequest request, EditorEventSink sink, CompletableFuture<EditorEventStream> opened) {}

        final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();
        final BlockingQueue<Stream> streams = new LinkedBlockingQueue<>();

        @Override
        public CompletionStage<EditorResponse> handle(EditorRequest request) {
            CompletableFuture<EditorResponse> response = new CompletableFuture<>();
            calls.add(new Call(request, response));
            return response;
        }

        @Override
        public CompletionStage<EditorEventStream> openEvents(EditorRequest request, EditorEventSink sink) {
            CompletableFuture<EditorEventStream> opened = new CompletableFuture<>();
            streams.add(new Stream(request, sink, opened));
            return opened;
        }
    }
}
