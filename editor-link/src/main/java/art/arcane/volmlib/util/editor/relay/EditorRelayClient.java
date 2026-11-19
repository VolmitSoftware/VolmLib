package art.arcane.volmlib.util.editor.relay;

import art.arcane.volmlib.util.editor.EditorIdentity;
import art.arcane.volmlib.util.editor.EditorLog;
import art.arcane.volmlib.util.editor.EditorProduct;
import art.arcane.volmlib.util.editor.EditorRefusal;
import art.arcane.volmlib.util.editor.StrictJson;
import art.arcane.volmlib.util.editor.transport.EditorEventSink;
import art.arcane.volmlib.util.editor.transport.EditorEventStream;
import art.arcane.volmlib.util.editor.transport.EditorRequest;
import art.arcane.volmlib.util.editor.transport.EditorRequestHandler;
import art.arcane.volmlib.util.editor.transport.EditorResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Connects this server out to the editor relay and answers the browser requests it forwards through the same
 * {@link EditorRequestHandler} as the direct listener. Frames are strict JSON objects of {@code type} challenge, register,
 * registered, request, response, event, close and error.
 *
 * <p>Runs on its own daemon thread and the JDK WebSocket threads and never touches world state. Reconnects with jittered
 * backoff, reads one frame at a time, refuses frames over {@code frameBytes}, pings a quiet relay, and signs a browser's
 * challenge header so the editor can check it reached this key before it sends a token or pairing secret. The relay can
 * still read everything it forwards. Each request is registered in frame order on the WebSocket thread and answered on a
 * virtual thread of its own, so one slow request does not hold up the frames after it.
 */
public final class EditorRelayClient implements AutoCloseable {
    public static final int PROTOCOL = 1;
    /** Events a stream may send before its opening response went out; past it the stream ends. */
    public static final int EARLY_EVENTS = 256;
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{43}");
    /** Keeps null members: documents carry them on purpose, such as an emptied slot's {@code "item": null}. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final int MAX_QUEUED_FRAMES = 1024;
    private static final long MAX_ID = 9007199254740991L;

    private final EditorProduct product;
    private final RelaySettings settings;
    private final EditorIdentity identity;
    private final EditorRequestHandler handler;
    private final EditorLog log;
    private final RelayBackoff backoff;
    private final Duration handshakeTimeout;
    private final Duration idleTimeout;
    private final ScheduledExecutorService scheduler;
    private final HttpClient http;
    private final ExecutorService requests;
    private final Object lock = new Object();
    private boolean running;
    private int attempt;
    /** The relay could not be reached since the last registration, and the log said so once. */
    private boolean unreachable;
    /** Why the last attempt failed while {@link #unreachable}. */
    private String unreachableReason = "";
    private Connection current;
    private ScheduledFuture<?> reconnect;

    public EditorRelayClient(EditorProduct product, RelaySettings settings, EditorIdentity identity, EditorRequestHandler handler, EditorLog log) {
        this(product, settings, identity, handler, log, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    /**
     * {@code idleTimeout}: how long without any frame, ping or pong from the relay before the connection counts as dead. A
     * third of it without one, the client pings, so a live relay always answers in time and a half-open socket (a relay gone
     * without closing it) is left and dialled again.
     */
    public EditorRelayClient(EditorProduct product, RelaySettings settings, EditorIdentity identity, EditorRequestHandler handler, EditorLog log,
                             Duration handshakeTimeout, Duration idleTimeout) {
        this.product = Objects.requireNonNull(product);
        this.settings = Objects.requireNonNull(settings);
        this.identity = Objects.requireNonNull(identity);
        this.handler = Objects.requireNonNull(handler);
        this.log = Objects.requireNonNull(log);
        this.backoff = new RelayBackoff(settings.reconnectMinSeconds() * 1000L, settings.reconnectMaxSeconds() * 1000L);
        this.handshakeTimeout = handshakeTimeout;
        this.idleTimeout = idleTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name(product.thread("relay")).factory());
        this.requests = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(product.thread("relay-request")).factory());
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** The challenge header a browser sends; a relayed answer to it carries the server key and proof headers. */
    public String challengeHeader() { return product.header("Challenge"); }

    /** Starts connecting at once. Does nothing when the relay is not enabled. */
    public void start() {
        if (!settings.enabled()) return;
        synchronized (lock) {
            if (running) return;
            running = true;
            attempt = 0;
        }
        scheduler.execute(this::connect);
    }

    /** True while a connection is registered with the relay. */
    public boolean isRegistered() {
        synchronized (lock) { return current != null && current.registered; }
    }

    /** Why the relay cannot be reached, while no attempt since the last registration got through. */
    public Optional<String> unreachableReason() {
        synchronized (lock) { return unreachable ? Optional.of(unreachableReason) : Optional.empty(); }
    }

    @Override
    public void close() {
        Connection connection;
        synchronized (lock) {
            running = false;
            if (reconnect != null) reconnect.cancel(false);
            connection = current;
            current = null;
        }
        if (connection != null) connection.end(true);
        scheduler.shutdown();
        try { scheduler.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        scheduler.shutdownNow();
        requests.shutdownNow();
        http.shutdownNow();
    }

    private void connect() {
        Connection connection = new Connection();
        synchronized (lock) {
            if (!running) return;
            current = connection;
        }
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(settings.agentUri(), connection)
                .whenComplete((WebSocket socket, Throwable failure) -> { if (failure != null) connection.fail("connect failed: " + reason(failure)); });
    }

    private void scheduleReconnect(Connection ended) {
        synchronized (lock) {
            if (current != ended) return;
            current = null;
            if (!running) return;
            long delay = backoff.delayMillis(attempt++, ThreadLocalRandom.current().nextDouble());
            log.debug().accept("Editor relay reconnect in " + delay + " ms (attempt " + attempt + ")");
            try { reconnect = scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS); }
            catch (RejectedExecutionException stopped) { running = false; }
        }
    }

    private static String reason(Throwable failure) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    /** One WebSocket to the relay, from open to close. */
    private final class Connection implements WebSocket.Listener {
        private final StringBuilder text = new StringBuilder();
        private final ArrayDeque<String> outbound = new ArrayDeque<>();
        private final Map<Long, Route> routes = new ConcurrentHashMap<>();
        private final AtomicInteger active = new AtomicInteger();
        private volatile WebSocket socket;
        private volatile boolean registered;
        private volatile long lastSeen = System.nanoTime();
        private long lastPing = System.nanoTime();
        private boolean pinging;
        private boolean sending;
        private boolean ended;
        private ScheduledFuture<?> watchdog;

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            lastSeen = System.nanoTime();
            long started = System.nanoTime();
            try { watchdog = scheduler.scheduleAtFixedRate(() -> watch(started), 250, 250, TimeUnit.MILLISECONDS); }
            catch (RejectedExecutionException stopped) {
                end(true);
                return;
            }
            webSocket.request(1);
        }

        private void watch(long started) {
            long now = System.nanoTime();
            long quiet = idleTimeout.toNanos() / 3;
            if (!registered && now - started > handshakeTimeout.toNanos()) fail("registration timed out");
            else if (now - lastSeen > idleTimeout.toNanos()) fail("no answer from the relay for " + idleTimeout.toMillis() + " ms");
            else if (now - lastSeen > quiet && now - lastPing > quiet) ping(now);
        }

        /** One ping at a time; a failed send ends the connection. Runs on the scheduler thread. */
        private void ping(long now) {
            WebSocket target = socket;
            if (target == null || pinging) return;
            pinging = true;
            lastPing = now;
            try {
                target.sendPing(ByteBuffer.allocate(0)).whenCompleteAsync((WebSocket sent, Throwable failure) -> {
                    pinging = false;
                    if (failure != null) fail("ping failed: " + reason(failure));
                }, scheduler);
            } catch (RuntimeException failure) {
                pinging = false;
                fail("ping failed: " + reason(failure));
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            lastSeen = System.nanoTime();
            text.append(data);
            if (text.length() > settings.frameBytes()) {
                fail("frame over " + settings.frameBytes() + " bytes");
                return null;
            }
            if (last) {
                String frame = text.toString();
                text.setLength(0);
                if (frame.getBytes(StandardCharsets.UTF_8).length > settings.frameBytes()) {
                    fail("frame over " + settings.frameBytes() + " bytes");
                    return null;
                }
                try { receive(StrictJson.object(frame)); }
                catch (IOException | RuntimeException invalid) {
                    fail("invalid frame: " + invalid.getMessage());
                    return null;
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            fail("binary frame");
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.nanoTime();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.nanoTime();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            end(false);
            log.debug().accept("Editor relay closed " + statusCode + " " + reason);
            scheduleReconnect(this);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) { fail(reason(error)); }

        private void receive(JsonObject frame) {
            String type = string(frame, "type");
            if (!registered) {
                switch (type) {
                    case "challenge" -> {
                        if (number(frame, "protocol") != PROTOCOL) throw new IllegalArgumentException("relay protocol " + frame.get("protocol"));
                        String nonce = string(frame, "nonce");
                        if (!NONCE.matcher(nonce).matches()) throw new IllegalArgumentException("challenge nonce");
                        JsonObject register = new JsonObject();
                        register.addProperty("type", "register");
                        register.addProperty("protocol", PROTOCOL);
                        register.addProperty("serverKey", identity.serverKey());
                        register.addProperty("signature", identity.sign(product.relayChallengePrefix() + nonce));
                        send(register);
                    }
                    case "registered" -> {
                        if (!identity.fingerprint().equals(string(frame, "fingerprint"))) throw new IllegalArgumentException("registered under another fingerprint");
                        registered = true;
                        synchronized (lock) {
                            attempt = 0;
                            unreachable = false;
                            unreachableReason = "";
                        }
                        log.info().accept("Editor relay registered " + identity.fingerprint() + " at " + settings.url());
                    }
                    case "error" -> refused(frame);
                    default -> throw new IllegalArgumentException("unexpected " + type + " before registration");
                }
                return;
            }
            switch (type) {
                case "request" -> request(frame);
                case "close" -> {
                    Route route = routes.remove(id(frame));
                    if (route != null) route.cancel();
                }
                case "error" -> refused(frame);
                default -> { }
            }
        }

        private void refused(JsonObject frame) { log.warn().accept("Editor relay refused the connection: " + frame.get("code") + " " + frame.get("message")); }

        private void request(JsonObject frame) {
            long id = id(frame);
            Map<String, String> headers = new LinkedHashMap<>();
            if (frame.has("headers")) for (Map.Entry<String, JsonElement> header : frame.getAsJsonObject("headers").entrySet()) headers.put(header.getKey(), header.getValue().getAsString());
            EditorRequest request;
            try { request = new EditorRequest(EditorRequest.Transport.RELAY, string(frame, "method"), string(frame, "path"), headers, Optional.ofNullable(frame.get("body"))); }
            catch (IllegalArgumentException invalid) {
                respond(id, EditorResponse.error(400, "invalid-request", invalid.getMessage()));
                return;
            }
            if (routes.containsKey(id)) {
                respond(id, EditorResponse.error(400, "invalid-request", "id is in use"));
                return;
            }
            if (active.incrementAndGet() > settings.concurrentRequests()) {
                active.decrementAndGet();
                respond(id, new EditorRefusal(EditorRefusal.Code.BUSY, "too many relayed requests", 1000, null).response());
                return;
            }
            Route route = new Route(id);
            routes.put(id, route);
            try { requests.execute(() -> run(route, request)); }
            catch (RejectedExecutionException stopping) {
                route.answer(new EditorRefusal(EditorRefusal.Code.BUSY, "the server is stopping", 1000, null).response(), false);
                active.decrementAndGet();
            }
        }

        private void run(Route route, EditorRequest request) {
            route.proof = proof(request);
            boolean stream = request.method().equals("GET") && request.route().equals("/v1/events");
            CompletionStage<?> answered;
            try {
                if (stream) {
                    answered = handler.openEvents(request, route).whenComplete((EditorEventStream opened, Throwable failure) -> {
                        if (failure != null) route.answer(internal(failure), false);
                        else route.opened(opened);
                    });
                } else {
                    answered = handler.handle(request).whenComplete((EditorResponse response, Throwable failure) -> route.answer(failure != null ? internal(failure) : response, false));
                }
            } catch (RuntimeException failure) {
                route.answer(internal(failure), false);
                answered = CompletableFuture.completedFuture(null);
            }
            answered.whenComplete((Object result, Throwable failure) -> active.decrementAndGet());
        }

        /** Proof headers for a request with a well-formed challenge header, else none. */
        private Map<String, String> proof(EditorRequest request) {
            return request.header(challengeHeader()).filter((String challenge) -> NONCE.matcher(challenge).matches())
                    .map((String challenge) -> Map.of(product.header("Server-Key"), identity.serverKey(), product.header("Proof"), identity.sign(product.proofPrefix() + challenge)))
                    .orElse(Map.of());
        }

        private EditorResponse internal(Throwable failure) {
            log.warn().accept("Editor relay request failed: " + reason(failure));
            return EditorResponse.error(500, "internal", "the request failed on the server");
        }

        /** Queues the response; false when it was over the frame limit and a too-large error went instead. */
        private boolean respond(long id, EditorResponse response) {
            JsonObject frame = new JsonObject();
            frame.addProperty("type", "response");
            frame.addProperty("id", id);
            frame.addProperty("status", response.status());
            if (!response.headers().isEmpty()) {
                JsonObject headers = new JsonObject();
                response.headers().forEach(headers::addProperty);
                frame.add("headers", headers);
            }
            response.body().ifPresent((JsonElement body) -> frame.add("body", body));
            if (send(frame)) return true;
            JsonObject tooLarge = new JsonObject();
            tooLarge.addProperty("type", "response");
            tooLarge.addProperty("id", id);
            tooLarge.addProperty("status", 413);
            tooLarge.add("body", GSON.toJsonTree(Map.of("error", Map.of("code", "too-large", "message", "the answer exceeds the relay frame limit; use the direct connection"))));
            send(tooLarge);
            return false;
        }

        /** Queues one frame; false when it is over the frame limit. */
        private boolean send(JsonObject frame) {
            String encoded = GSON.toJson(frame);
            if (encoded.getBytes(StandardCharsets.UTF_8).length > settings.frameBytes()) return false;
            synchronized (this) {
                if (ended) return true;
                if (outbound.size() >= MAX_QUEUED_FRAMES) {
                    scheduler.execute(() -> fail("send queue full"));
                    return true;
                }
                outbound.add(encoded);
                if (sending) return true;
                sending = true;
            }
            scheduler.execute(this::pump);
            return true;
        }

        private void pump() {
            String next;
            synchronized (this) {
                next = ended ? null : outbound.poll();
                if (next == null) {
                    sending = false;
                    return;
                }
            }
            WebSocket target = socket;
            if (target == null) return;
            target.sendText(next, true).whenCompleteAsync((WebSocket sent, Throwable failure) -> {
                if (failure != null) fail("send failed: " + reason(failure));
                else pump();
            }, scheduler);
        }

        private void fail(String why) {
            if (!end(false)) return;
            boolean first;
            synchronized (lock) {
                first = !registered && !unreachable;
                if (!registered) {
                    unreachable = true;
                    unreachableReason = why;
                }
            }
            // Once per outage: a browser pairing over the relay cannot reach this server until it registers.
            if (first) log.warn().accept("Editor relay unreachable at " + settings.url() + ": " + why + "; retrying in the background");
            else log.debug().accept("Editor relay connection ended: " + why);
            WebSocket target = socket;
            if (target != null) target.abort();
            scheduleReconnect(this);
        }

        /** Ends the connection once: closes every stream and drops every pending answer. */
        private boolean end(boolean closeSocket) {
            List<Route> open;
            synchronized (this) {
                if (ended) return false;
                ended = true;
                outbound.clear();
                open = new ArrayList<>(routes.values());
                routes.clear();
            }
            if (watchdog != null) watchdog.cancel(false);
            for (Route route : open) route.cancel();
            WebSocket target = socket;
            if (closeSocket && target != null) {
                try { target.sendClose(WebSocket.NORMAL_CLOSURE, "server stopping"); }
                catch (RuntimeException ignored) { target.abort(); }
            }
            return true;
        }

        /**
         * One request or event stream, keyed by the relay's id; it is also the stream's event sink. Events sent before the
         * stream's response went out wait and follow it, and a stream that cannot deliver an event is closed.
         */
        private final class Route implements EditorEventSink {
            private final long id;
            private final ArrayDeque<JsonObject> early = new ArrayDeque<>();
            /** Signed before the handler runs; empty when the browser sent no challenge. */
            private volatile Map<String, String> proof = Map.of();
            private boolean answered, streaming, cancelled;
            /** The stream ended before its response went out; the close follows the response. */
            private boolean ending;
            private Runnable onClose;

            private Route(long id) { this.id = id; }

            private void answer(EditorResponse response, boolean keep) {
                EditorResponse proved = proof.isEmpty() ? response : withHeaders(response, proof);
                if (!keep) {
                    synchronized (this) {
                        if (answered || cancelled) return;
                        answered = true;
                        early.clear();
                    }
                    routes.remove(id, this);
                    respond(id, proved);
                    return;
                }
                boolean delivered, end;
                synchronized (this) {
                    if (answered || cancelled) return;
                    answered = true;
                    delivered = respond(id, proved);
                    boolean failed = false;
                    if (delivered) {
                        streaming = true;
                        while (!early.isEmpty() && !failed) failed = !Connection.this.send(event(early.pollFirst()));
                        early.clear();
                    }
                    end = ending || failed;
                }
                if (!delivered) cancel();
                else if (end) finish();
            }

            private void opened(EditorEventStream stream) {
                boolean open = stream.open();
                Runnable closeNow = null;
                synchronized (this) {
                    if (open) {
                        onClose = stream.onClose();
                        if (cancelled) closeNow = onClose;
                    }
                }
                if (closeNow != null) {
                    closeNow.run();
                    return;
                }
                answer(stream.response(), open);
            }

            /** The browser left, the relay closed the id, or the connection ended: nothing more goes to the relay. */
            private void cancel() {
                Runnable close;
                synchronized (this) {
                    if (cancelled) return;
                    cancelled = true;
                    early.clear();
                    close = onClose;
                }
                routes.remove(id, this);
                if (close != null) close.run();
            }

            /** Ends an answered stream from this side: a close frame to the relay, then the stream's own cleanup. */
            private void finish() {
                Runnable close;
                synchronized (this) {
                    if (cancelled) return;
                    cancelled = true;
                    close = onClose;
                }
                if (routes.remove(id, this)) {
                    JsonObject frame = new JsonObject();
                    frame.addProperty("type", "close");
                    frame.addProperty("id", id);
                    Connection.this.send(frame);
                }
                if (close != null) close.run();
            }

            private JsonObject event(JsonObject event) {
                JsonObject frame = new JsonObject();
                frame.addProperty("type", "event");
                frame.addProperty("id", id);
                frame.add("event", event);
                return frame;
            }

            @Override
            public boolean send(JsonObject event) {
                synchronized (this) {
                    // A refused stream was answered without opening, so it takes no events.
                    if (cancelled || ending || answered && !streaming) return false;
                    if (!streaming) {
                        if (early.size() >= EARLY_EVENTS) {
                            ending = true;
                            return false;
                        }
                        early.addLast(event);
                        return true;
                    }
                    if (Connection.this.send(event(event))) return true;
                }
                finish();
                return false;
            }

            @Override
            public void close() {
                synchronized (this) {
                    if (cancelled) return;
                    if (!answered) {
                        ending = true;
                        return;
                    }
                }
                finish();
            }
        }
    }

    private static EditorResponse withHeaders(EditorResponse response, Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(response.headers());
        merged.putAll(extra);
        return new EditorResponse(response.status(), merged, response.body());
    }

    private static String string(JsonObject frame, String field) {
        JsonElement value = frame.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(field + " must be a string");
        return value.getAsString();
    }

    private static long number(JsonObject frame, String field) {
        JsonElement value = frame.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(field + " must be a number");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        try { return primitive.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException fraction) { throw new IllegalArgumentException(field + " must be an integer"); }
    }

    private static long id(JsonObject frame) {
        long id = number(frame, "id");
        if (id < 1 || id > MAX_ID) throw new IllegalArgumentException("id out of range");
        return id;
    }
}
