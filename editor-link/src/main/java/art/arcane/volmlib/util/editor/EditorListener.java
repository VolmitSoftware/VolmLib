package art.arcane.volmlib.util.editor;

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
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * The embedded HTTP/1.1 and WebSocket listener of the direct path, at the product's base path ({@code /animus/v1/}). Plain
 * HTTP only on a loopback address; anything else needs TLS from the operator's PEM files. Every connection runs on its own
 * virtual thread and the total is bounded by {@code openConnections}; heads are limited to 16 KiB and bodies to the
 * configured sizes before they are read. Requests go to the shared {@link EditorRequestHandler}, as relayed ones do. The TLS
 * handshake, the WebSocket auth frame and each response have a total deadline, so a client that trickles bytes or stops
 * reading cannot hold a connection. A trusted proxy is exempt from the per-address limit and its X-Forwarded-For is believed.
 */
public final class EditorListener implements AutoCloseable {
    /** Connections one address may hold at once, so slow or idle clients from one place cannot take every slot. */
    public static final int CONNECTIONS_PER_ADDRESS = 16;
    /** The largest body a request without a valid token may send: pairing and the open endpoints. */
    public static final int OPEN_BODY_BYTES = 4096;
    private static final int HEAD_BYTES = 16 * 1024, WEBSOCKET_FRAME_BYTES = 64 * 1024, REQUESTS_PER_CONNECTION = 1000;
    /** An idle connection waits this long for the next request; once its first byte arrives the whole head must follow within {@link #HEAD_MILLIS}. */
    private static final int IDLE_MILLIS = 15_000, HEAD_MILLIS = 5_000;
    /**
     * A body without a token must arrive whole within {@link #OPEN_BODY_MILLIS}; an authenticated one gets {@link #BODY_MILLIS}
     * plus a millisecond for each {@link #BODY_BYTES_PER_MILLI} bytes, however slowly it trickles. Responses get the same.
     */
    private static final int OPEN_BODY_MILLIS = 5_000, BODY_MILLIS = 30_000, BODY_BYTES_PER_MILLI = 16;
    private static final int HANDSHAKE_MILLIS = 10_000, AUTH_MILLIS = 10_000;
    /** Keeps null members: documents carry them on purpose, such as an emptied slot's {@code "item": null}. */
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
    private static final String WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final EditorProduct product;
    private volatile ListenerSettings settings;
    private final Path dataDirectory;
    private final EditorRequestHandler handler;
    private final Consumer<String> log;
    private final Semaphore connections;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> perAddress = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor deadlines;
    private volatile ServerSocket server;
    private volatile boolean closed;

    /** {@code dataDirectory} is where relative TLS file names resolve; {@code log} hears that the listener stopped. */
    public EditorListener(EditorProduct product, ListenerSettings settings, Path dataDirectory, EditorRequestHandler handler, Consumer<String> log) {
        this.product = Objects.requireNonNull(product);
        this.settings = Objects.requireNonNull(settings);
        this.dataDirectory = Objects.requireNonNull(dataDirectory);
        this.handler = Objects.requireNonNull(handler);
        this.log = Objects.requireNonNull(log);
        connections = new Semaphore(settings.openConnections());
        deadlines = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name(product.thread("deadlines")).factory());
        deadlines.setRemoveOnCancelPolicy(true);
    }

    public synchronized void start() throws IOException {
        if (server != null) throw new IllegalStateException("The editor listener is already running");
        ListenerSettings current = settings;
        InetAddress address = InetAddress.getByName(current.listenAddress());
        if (!current.tls() && !address.isLoopbackAddress())
            throw new IOException("The editor listener serves plain HTTP only on loopback; " + current.listenAddress() + " needs tlsCertificate and tlsPrivateKey");
        ServerSocket socket;
        if (current.tls()) {
            SSLContext context = PemTls.context(dataDirectory.resolve(current.tlsCertificate()), dataDirectory.resolve(current.tlsPrivateKey()));
            SSLServerSocket secure = (SSLServerSocket) context.getServerSocketFactory().createServerSocket();
            secure.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            socket = secure;
        } else socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(address, current.port()), 128);
        server = socket;
        Thread.ofPlatform().daemon().name(product.thread("listener")).start(this::accept);
    }

    /** A settings change that keeps the socket: body limits and trusted proxies apply to the next request. */
    public void settings(ListenerSettings next) {
        if (settings.rebind(next)) throw new IllegalArgumentException("These settings need a new listener");
        settings = next;
    }

    /** The bound port, which differs from the setting when it was 0. */
    public int port() {
        ServerSocket socket = server;
        return socket == null ? -1 : socket.getLocalPort();
    }

    public String describe() {
        ServerSocket socket = server;
        if (socket == null) return "listener stopped";
        ListenerSettings current = settings;
        return "listener " + current.listenAddress() + ":" + socket.getLocalPort() + (current.tls() ? " (https)" : " (http)")
                + " " + (current.openConnections() - connections.availablePermits()) + "/" + current.openConnections() + " connections";
    }

    @Override
    public void close() {
        closed = true;
        deadlines.shutdownNow();
        ServerSocket socket = server;
        server = null;
        if (socket != null) {
            try { socket.close(); }
            catch (IOException ignored) { /* Closing an accepting socket only ends the accept loop. */ }
        }
        for (Socket connection : open) closeQuietly(connection);
    }

    private void accept() {
        while (!closed) {
            Socket socket;
            try {
                ServerSocket current = server;
                if (current == null) return;
                socket = current.accept();
            } catch (IOException stopped) {
                if (!closed) log.accept("Editor listener stopped accepting: " + stopped.getMessage());
                return;
            }
            String address = EditorAddresses.key(socket.getInetAddress());
            boolean counted = !settings.trustsProxy(socket.getInetAddress());
            if (!connections.tryAcquire()) {
                refuse(socket, "The editor listener has its maximum of connections open.");
                continue;
            }
            if (counted && perAddress.merge(address, 1, Integer::sum) > CONNECTIONS_PER_ADDRESS) {
                leave(address);
                connections.release();
                refuse(socket, "This address has its maximum of " + CONNECTIONS_PER_ADDRESS + " editor connections open.");
                continue;
            }
            open.add(socket);
            if (closed) {
                open.remove(socket);
                if (counted) leave(address);
                connections.release();
                closeQuietly(socket);
                return;
            }
            Thread.ofVirtual().name(product.thread("connection")).start(() -> {
                try { serve(socket); }
                finally {
                    open.remove(socket);
                    if (counted) leave(address);
                    connections.release();
                }
            });
        }
    }

    private void leave(String address) { perAddress.computeIfPresent(address, (String key, Integer count) -> count <= 1 ? null : count - 1); }

    /**
     * Answers busy over plain HTTP, on the accept thread: a new socket's send buffer takes the short answer at once. A TLS
     * connection is closed unanswered, since an answer would first cost a handshake.
     */
    private static void refuse(Socket socket, String reason) {
        try (socket) {
            if (!(socket instanceof SSLSocket)) socket.getOutputStream().write(encode(new EditorRefusal(EditorRefusal.Code.BUSY, reason, 1000, null).response(), false));
        } catch (IOException ignored) { /* The client left first. */ }
    }

    /** Runs {@code expire} after {@code millis} unless the returned future is cancelled first. */
    private Future<?> deadline(long millis, Runnable expire) {
        try { return deadlines.schedule(expire, millis, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException stopping) {
            expire.run();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static void closeQuietly(Socket socket) {
        try { socket.close(); }
        catch (IOException ignored) { /* Closing twice is harmless. */ }
    }

    private record Head(String method, String target, Map<String, String> headers) {}

    private void serve(Socket socket) {
        InetAddress peer = socket.getInetAddress();
        String base = product.basePath();
        try (socket) {
            socket.setTcpNoDelay(true);
            if (socket instanceof SSLSocket secure) {
                Future<?> late = deadline(HANDSHAKE_MILLIS, () -> closeQuietly(socket));
                try {
                    secure.setSoTimeout(HANDSHAKE_MILLIS);
                    secure.startHandshake();
                } finally { late.cancel(false); }
            }
            InputStream input = new BufferedInputStream(socket.getInputStream());
            OutputStream output = socket.getOutputStream();
            for (int served = 0; served < REQUESTS_PER_CONNECTION && !closed; served++) {
                Head head;
                try { head = head(socket, input); }
                catch (EOFException | SocketTimeoutException idle) { return; }
                catch (EditorRefusal invalid) {
                    respond(socket, output, invalid.response(), false);
                    return;
                }
                if (head == null) return;
                if (!head.target().startsWith(base + "/v")) {
                    respond(socket, output, refusal(EditorRefusal.Code.NOT_FOUND, "The editor API is under " + base + "/v1/."), false);
                    return;
                }
                String target = head.target().substring(base.length());
                if (!target.startsWith("/v1/")) {
                    respond(socket, output, refusal(EditorRefusal.Code.UNSUPPORTED_PROTOCOL, "This server speaks the editor API at " + base + "/v1/."), false);
                    return;
                }
                if ("websocket".equalsIgnoreCase(head.headers().getOrDefault("upgrade", ""))) {
                    websocket(socket, input, output, head, target, peer);
                    return;
                }
                if (head.headers().containsKey("transfer-encoding")) {
                    respond(socket, output, refusal(EditorRefusal.Code.INVALID_REQUEST, "Send a Content-Length body; chunked bodies are not accepted."), false);
                    return;
                }
                long length;
                try { length = Long.parseLong(head.headers().getOrDefault("content-length", "0").strip()); }
                catch (NumberFormatException invalid) {
                    respond(socket, output, refusal(EditorRefusal.Code.INVALID_REQUEST, "Content-Length is not a number."), false);
                    return;
                }
                // The token and scope are checked from the head, so only an authenticated request may send a large body.
                EditorRequestHandler.Admission admission = handler.admit(new EditorRequest(EditorRequest.Transport.DIRECT, head.method(), target, headers(head, peer), Optional.empty()));
                if (admission.refusal().isPresent()) {
                    respond(socket, output, admission.refusal().get(), false);
                    return;
                }
                ListenerSettings current = settings;
                long limit = admission.authenticated() ? current.bodyLimit(target) : OPEN_BODY_BYTES;
                if (length < 0 || length > limit) {
                    respond(socket, output, refusal(EditorRefusal.Code.TOO_LARGE, "Request bodies are limited to " + limit + " bytes here."), false);
                    return;
                }
                byte[] body;
                try { body = readBody(socket, input, (int) length, admission.authenticated() ? BODY_MILLIS + length / BODY_BYTES_PER_MILLI : OPEN_BODY_MILLIS); }
                catch (SocketTimeoutException slow) { return; }
                if (body.length != length) return;
                EditorResponse response;
                try {
                    EditorRequest request = new EditorRequest(EditorRequest.Transport.DIRECT, head.method(), target, headers(head, peer), body(current, target, body));
                    response = handler.handle(request).toCompletableFuture().get(120, TimeUnit.SECONDS);
                } catch (EditorRefusal invalid) {
                    response = invalid.response();
                } catch (TimeoutException slow) {
                    response = new EditorRefusal(EditorRefusal.Code.BUSY, "The request did not finish in time; check its status before retrying.", 1000, null).response();
                } catch (ExecutionException failure) {
                    response = refusal(EditorRefusal.Code.INTERNAL, "The server could not complete the request.");
                } catch (InterruptedException stopping) {
                    Thread.currentThread().interrupt();
                    return;
                }
                boolean keepAlive = !"close".equalsIgnoreCase(head.headers().getOrDefault("connection", ""));
                respond(socket, output, response, keepAlive);
                if (!keepAlive) return;
            }
        } catch (IOException gone) {
            // The client closed the connection, missed a deadline or failed the TLS handshake; nothing is owed to it.
        }
    }

    private static EditorResponse refusal(EditorRefusal.Code code, String message) { return new EditorRefusal(code, message).response(); }

    /** Reads exactly {@code length} bytes, or fewer at end of stream, within {@code millis} in all. */
    private static byte[] readBody(Socket socket, InputStream input, int length, long millis) throws IOException {
        byte[] body = new byte[length];
        long deadline = System.nanoTime() + millis * 1_000_000L;
        int read = 0;
        while (read < length) {
            long left = (deadline - System.nanoTime()) / 1_000_000;
            if (left <= 0) throw new SocketTimeoutException("The request body was too slow");
            socket.setSoTimeout((int) Math.min(left, Integer.MAX_VALUE));
            int count = input.read(body, read, length - read);
            if (count < 0) return Arrays.copyOf(body, read);
            read += count;
        }
        return body;
    }

    /** The client's headers with its address in the product's peer header; a client cannot supply its own. */
    private Map<String, String> headers(Head head, InetAddress peer) {
        Map<String, String> headers = new LinkedHashMap<>(head.headers());
        headers.remove(product.peerHeader().toLowerCase(Locale.ROOT));
        headers.put(product.peerHeader(), client(peer, head.headers().get("x-forwarded-for")));
        return headers;
    }

    /**
     * The socket's address, or behind trusted proxies the rightmost X-Forwarded-For address that is not one of them: each
     * proxy appends the address it heard from, so entries left of the first untrusted one are the client's word.
     */
    private String client(InetAddress peer, String forwarded) {
        ListenerSettings current = settings;
        InetAddress client = peer;
        if (forwarded != null) {
            String[] hops = forwarded.split(",");
            for (int index = hops.length - 1; index >= 0 && current.trustsProxy(client); index--) {
                Optional<InetAddress> hop = EditorAddresses.literal(hops[index]);
                if (hop.isEmpty()) break;
                client = hop.get();
            }
        }
        return client.getHostAddress();
    }

    /** Strict JSON for API bodies; the upload route's raw bytes as a base64 string. */
    private static Optional<JsonElement> body(ListenerSettings current, String target, byte[] bytes) throws EditorRefusal {
        if (bytes.length == 0) return Optional.empty();
        String route = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        if (!current.uploadRoute().isEmpty() && route.equals(current.uploadRoute())) return Optional.of(new JsonPrimitive(Base64.getEncoder().encodeToString(bytes)));
        try { return Optional.of(StrictJson.object(new String(bytes, StandardCharsets.UTF_8))); }
        catch (IOException invalid) { throw new EditorRefusal(EditorRefusal.Code.INVALID_REQUEST, "The body is not one JSON object: " + invalid.getMessage()); }
    }

    /**
     * Reads a request line and headers, or null at a clean end of stream. The first byte may take {@link #IDLE_MILLIS}; the
     * rest of the head must arrive within {@link #HEAD_MILLIS} of it, however slowly it trickles.
     */
    private static Head head(Socket socket, InputStream input) throws IOException, EditorRefusal {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int state = 0;
        socket.setSoTimeout(IDLE_MILLIS);
        long deadline = 0;
        while (state < 4) {
            if (deadline != 0) {
                long left = (deadline - System.nanoTime()) / 1_000_000;
                if (left <= 0) throw new SocketTimeoutException("The request head was too slow");
                socket.setSoTimeout((int) left);
            }
            int read = input.read();
            if (deadline == 0) deadline = System.nanoTime() + HEAD_MILLIS * 1_000_000L;
            if (read < 0) {
                if (bytes.size() == 0) return null;
                throw new EOFException();
            }
            bytes.write(read);
            if (bytes.size() > HEAD_BYTES) throw new EditorRefusal(EditorRefusal.Code.TOO_LARGE, "Request heads are limited to " + HEAD_BYTES + " bytes.");
            state = switch (state) {
                case 0, 2 -> read == '\r' ? state + 1 : 0;
                case 1, 3 -> read == '\n' ? state + 1 : read == '\r' ? 1 : 0;
                default -> 0;
            };
        }
        String[] lines = bytes.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3 || !request[2].startsWith("HTTP/1.") || !request[1].startsWith("/"))
            throw new EditorRefusal(EditorRefusal.Code.INVALID_REQUEST, "Expected an HTTP/1.1 request line.");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isEmpty()) continue;
            int colon = lines[index].indexOf(':');
            if (colon < 1) throw new EditorRefusal(EditorRefusal.Code.INVALID_REQUEST, "Malformed header line.");
            String name = lines[index].substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String value = lines[index].substring(colon + 1).strip();
            if (headers.containsKey(name) && (name.equals("content-length") || name.equals("authorization") || name.equals("origin")))
                throw new EditorRefusal(EditorRefusal.Code.INVALID_REQUEST, "Header " + name + " appears twice.");
            headers.merge(name, value, (String first, String second) -> first + ", " + second);
        }
        return new Head(request[0], request[1], headers);
    }

    /** Writes a response within a deadline for its size, so a client that stops reading cannot hold the connection. */
    private void respond(Socket socket, OutputStream output, EditorResponse response, boolean keepAlive) throws IOException {
        byte[] encoded = encode(response, keepAlive);
        Future<?> late = deadline(BODY_MILLIS + encoded.length / BODY_BYTES_PER_MILLI, () -> closeQuietly(socket));
        try {
            output.write(encoded);
            output.flush();
        } finally { late.cancel(false); }
    }

    private static byte[] encode(EditorResponse response, boolean keepAlive) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(response.status()).append(' ').append(reason(response.status())).append("\r\n");
        byte[] body = response.body().map((JsonElement json) -> GSON.toJson(json).getBytes(StandardCharsets.UTF_8)).orElse(new byte[0]);
        if (response.body().isPresent()) head.append("Content-Type: application/json; charset=utf-8\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        if (!response.headers().containsKey("Cache-Control")) head.append("Cache-Control: no-store\r\n");
        head.append("X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n");
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            if (header.getKey().matches("[A-Za-z0-9-]+") && !header.getValue().contains("\r") && !header.getValue().contains("\n"))
                head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n\r\n");
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(head.length() + body.length);
        encoded.writeBytes(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        encoded.writeBytes(body);
        return encoded.toByteArray();
    }

    private static String reason(int status) {
        return switch (status) {
            case 101 -> "Switching Protocols"; case 200 -> "OK"; case 201 -> "Created"; case 204 -> "No Content"; case 304 -> "Not Modified";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 403 -> "Forbidden"; case 404 -> "Not Found"; case 409 -> "Conflict";
            case 410 -> "Gone"; case 413 -> "Content Too Large"; case 422 -> "Unprocessable Content"; case 428 -> "Precondition Required";
            case 429 -> "Too Many Requests"; case 503 -> "Service Unavailable"; case 504 -> "Gateway Timeout"; default -> "Error";
        };
    }

    private void websocket(Socket socket, InputStream input, OutputStream output, Head head, String target, InetAddress peer) throws IOException {
        String key = head.headers().get("sec-websocket-key");
        String route = target.contains("?") ? target.substring(0, target.indexOf('?')) : target;
        if (!head.method().equals("GET") || !route.equals("/v1/events") || key == null || !"13".equals(head.headers().get("sec-websocket-version"))
                || !head.headers().getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade")) {
            respond(socket, output, refusal(EditorRefusal.Code.INVALID_REQUEST, "Only GET " + product.basePath() + "/v1/events upgrades to a WebSocket (version 13)."), false);
            return;
        }
        String accept;
        try { accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException missing) { throw new IOException(missing); }
        output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
        WebSocketSink sink = new WebSocketSink(socket, output, product.thread("events"));
        try { stream(socket, input, sink, head, target, peer); }
        finally {
            sink.close(1000, "closed");
            sink.awaitWritten();
        }
    }

    /** Authenticates the stream from the browser's first frame, then serves it until either side closes. */
    private void stream(Socket socket, InputStream input, WebSocketSink sink, Head head, String target, InetAddress peer) throws IOException {
        // A browser cannot set headers on a WebSocket, so its first frame carries the token, within 10 seconds in all.
        socket.setSoTimeout(AUTH_MILLIS);
        Future<?> late = deadline(AUTH_MILLIS, () -> sink.close(4401, "unauthorized"));
        String token;
        try {
            String first = firstText(input, sink);
            if (first == null) return;
            JsonObject frame = StrictJson.object(first);
            if (frame.size() != 2 || !frame.has("type") || !"auth".equals(frame.get("type").getAsString()) || !frame.has("token") || !frame.get("token").isJsonPrimitive())
                throw new IOException("expected an auth frame");
            token = frame.get("token").getAsString();
        } catch (SocketTimeoutException slow) {
            sink.close(4401, "unauthorized");
            return;
        } catch (IOException | RuntimeException invalid) {
            sink.close(4400, "invalid-request");
            return;
        } finally { late.cancel(false); }
        Map<String, String> headers = headers(head, peer);
        headers.put("Authorization", "Bearer " + token);
        EditorEventStream stream;
        try {
            stream = handler.openEvents(new EditorRequest(EditorRequest.Transport.DIRECT, "GET", target, headers, Optional.empty()), sink).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException failure) {
            sink.close(1011, "internal");
            return;
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
            sink.close(1001, "server-stopping");
            return;
        }
        if (!stream.open()) {
            String code = stream.response().body().filter(JsonElement::isJsonObject)
                    .map((JsonElement body) -> body.getAsJsonObject().getAsJsonObject("error").get("code").getAsString()).orElse("refused");
            sink.close(4000 + stream.response().status(), code);
            return;
        }
        try {
            socket.setSoTimeout(75_000);
            Thread pinger = Thread.ofVirtual().start(() -> {
                try {
                    while (!sink.closed && !closed) {
                        Thread.sleep(30_000);
                        sink.frame(0x9, new byte[0]);
                    }
                } catch (InterruptedException | IOException stopped) { /* The connection ended. */ }
            });
            try { frames(input, sink); }
            finally { pinger.interrupt(); }
        } finally {
            stream.onClose().run();
            sink.close(1000, "closed");
        }
    }

    /** The first text frame, answering pings meanwhile; null when the browser leaves first. */
    private static String firstText(InputStream input, WebSocketSink sink) throws IOException {
        while (true) {
            Frame frame = frame(input, sink);
            if (frame == null) return null;
            if (frame.opcode() == 0x1) return new String(frame.payload(), StandardCharsets.UTF_8);
            if (frame.opcode() == 0x9) sink.frame(0xA, frame.payload());
            else if (frame.opcode() == 0x8) {
                sink.close(1000, "closed");
                return null;
            }
        }
    }

    private record Frame(int opcode, byte[] payload) {}

    /** One masked, unfragmented client frame of at most 64 KiB, or null at the end of the stream or after a refused frame. */
    private static Frame frame(InputStream input, WebSocketSink sink) throws IOException {
        int first = input.read(), second = input.read();
        if (first < 0 || second < 0) return null;
        boolean fin = (first & 0x80) != 0, masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) length = ((long) input.read() << 8) | input.read();
        else if (length == 127) {
            length = 0;
            for (int index = 0; index < 8; index++) length = (length << 8) | input.read();
        }
        if (!masked || !fin || length < 0 || length > WEBSOCKET_FRAME_BYTES) {
            sink.close(1009, "limit");
            return null;
        }
        byte[] mask = input.readNBytes(4);
        byte[] payload = input.readNBytes((int) length);
        if (mask.length != 4 || payload.length != length) return null;
        for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
        return new Frame(first & 0x0F, payload);
    }

    /** After authentication the browser only pings and closes; other text is ignored. */
    private static void frames(InputStream input, WebSocketSink sink) throws IOException {
        while (!sink.closed) {
            Frame frame = frame(input, sink);
            if (frame == null) return;
            switch (frame.opcode()) {
                case 0x1, 0xA -> { }
                case 0x8 -> {
                    sink.close(1000, "closed");
                    return;
                }
                case 0x9 -> sink.frame(0xA, frame.payload());
                default -> {
                    sink.close(1003, "unsupported");
                    return;
                }
            }
        }
    }

    /**
     * Server-to-browser frames, unmasked. Callers only queue them; one writer thread per connection does the socket writes,
     * so a browser that stops reading blocks nobody else. Past {@link #OUTBOUND_BYTES} waiting, the backlog is dropped and the
     * stream closes with reason {@code limit}, which the editor answers by reconnecting and reloading what changed.
     */
    private static final class WebSocketSink implements EditorEventSink {
        static final int OUTBOUND_BYTES = 1024 * 1024;
        private static final long CLOSE_GRACE_MILLIS = 2000;
        private final Socket socket;
        private final OutputStream output;
        private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
        private final Thread writer;
        private int queued;
        /** No frame but the close is accepted any more. */
        volatile boolean closed;
        /** The close frame is queued; the writer ends after it. */
        private boolean finished;

        WebSocketSink(Socket socket, OutputStream output, String threadName) {
            this.socket = socket;
            this.output = output;
            writer = Thread.ofVirtual().name(threadName).start(this::write);
        }

        void frame(int opcode, byte[] payload) throws IOException {
            byte[] encoded = encode(opcode, payload);
            synchronized (this) {
                if (closed) throw new SocketException("closed");
                if (queued + encoded.length > OUTBOUND_BYTES) {
                    overflow();
                    throw new SocketException("limit");
                }
                enqueue(encoded);
            }
        }

        @Override
        public boolean send(JsonObject event) {
            byte[] payload = GSON.toJson(event).getBytes(StandardCharsets.UTF_8);
            if ("closing".equals(event.get("type").getAsString())) {
                // The reason goes straight after what was already on the wire: the browser reloads anyway.
                synchronized (this) {
                    if (closed) return false;
                    dropBacklog();
                    enqueue(encode(0x1, payload));
                }
                return true;
            }
            try {
                frame(0x1, payload);
                return true;
            } catch (IOException gone) {
                return false;
            }
        }

        @Override
        public void close() { close(1000, "closing"); }

        void close(int code, String reason) {
            byte[] text = reason.getBytes(StandardCharsets.UTF_8);
            byte[] payload = new byte[2 + Math.min(text.length, 120)];
            payload[0] = (byte) (code >>> 8);
            payload[1] = (byte) code;
            System.arraycopy(text, 0, payload, 2, payload.length - 2);
            synchronized (this) {
                if (finished) return;
                closed = true;
                finished = true;
                enqueue(encode(0x8, payload));
            }
            // A browser that reads nothing would hold the writer forever; the socket closes after a grace period regardless.
            Thread.ofVirtual().start(() -> {
                try { writer.join(CLOSE_GRACE_MILLIS); }
                catch (InterruptedException stopping) { Thread.currentThread().interrupt(); }
                closeSocket();
            });
        }

        /** Waits briefly for queued frames, the close last, to reach the socket. */
        void awaitWritten() {
            try { writer.join(CLOSE_GRACE_MILLIS); }
            catch (InterruptedException stopping) { Thread.currentThread().interrupt(); }
        }

        private void overflow() {
            dropBacklog();
            JsonObject closing = new JsonObject();
            closing.addProperty("type", "closing");
            closing.addProperty("reason", "limit");
            enqueue(encode(0x1, GSON.toJson(closing).getBytes(StandardCharsets.UTF_8)));
            Thread.ofVirtual().start(() -> close(1000, "limit"));
            closed = true;
        }

        private void dropBacklog() {
            queue.clear();
            queued = 0;
        }

        private void enqueue(byte[] encoded) {
            queue.addLast(encoded);
            queued += encoded.length;
            notifyAll();
        }

        private void write() {
            try {
                while (true) {
                    byte[] next;
                    synchronized (this) {
                        while (queue.isEmpty() && !finished) wait();
                        next = queue.pollFirst();
                        if (next == null) return;
                        queued -= next.length;
                    }
                    output.write(next);
                    output.flush();
                }
            } catch (IOException | InterruptedException gone) {
                synchronized (this) {
                    closed = true;
                    finished = true;
                    dropBacklog();
                }
            } finally { closeSocket(); }
        }

        private void closeSocket() {
            try { socket.close(); }
            catch (IOException ignored) { /* Closing twice is harmless. */ }
        }

        private static byte[] encode(int opcode, byte[] payload) {
            ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 10);
            frame.write(0x80 | opcode);
            if (payload.length < 126) frame.write(payload.length);
            else if (payload.length < 65536) {
                frame.write(126);
                frame.write(payload.length >>> 8);
                frame.write(payload.length & 0xFF);
            } else {
                frame.write(127);
                for (int shift = 56; shift >= 0; shift -= 8) frame.write((int) (((long) payload.length >>> shift) & 0xFF));
            }
            frame.writeBytes(payload);
            return frame.toByteArray();
        }
    }
}
