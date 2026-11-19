package art.arcane.volmlib.util.editor.relay;

import art.arcane.volmlib.util.editor.EditorFixtures;
import art.arcane.volmlib.util.editor.EditorProduct;
import art.arcane.volmlib.util.editor.PairingCode;
import art.arcane.volmlib.util.editor.StrictJson;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A minimal RFC 6455 server on loopback that plays the relay's /agent side for client tests. It answers every ping. */
final class FakeRelay implements AutoCloseable {
    private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Gson GSON = new Gson();

    private final ServerSocket server;
    private final BlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<Session> all = new CopyOnWriteArrayList<>();

    FakeRelay() throws IOException {
        server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptLoop, "fake-relay");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String url() { return "ws://127.0.0.1:" + server.getLocalPort(); }

    /** The next connection whose handshake completed at /agent. */
    Session accept(Duration timeout) throws InterruptedException {
        Session session = sessions.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (session == null) throw new AssertionError("no connection within " + timeout);
        return session;
    }

    boolean noConnection(Duration wait) throws InterruptedException {
        return sessions.poll(wait.toMillis(), TimeUnit.MILLISECONDS) == null;
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread reader = new Thread(() -> serve(socket), "fake-relay-session");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException closed) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try {
            InputStream input = socket.getInputStream();
            String head = readHead(input);
            String path = head.substring(head.indexOf(' ') + 1, head.indexOf(' ', head.indexOf(' ') + 1));
            String key = null;
            for (String line : head.split("\r\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals("sec-websocket-key")) key = line.substring(colon + 1).trim();
            }
            if (key == null || !path.equals("/agent")) {
                socket.getOutputStream().write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                socket.close();
                return;
            }
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + ACCEPT_GUID).getBytes(StandardCharsets.US_ASCII)));
            socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "
                    + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            Session session = new Session(socket);
            all.add(session);
            sessions.add(session);
            session.readLoop();
        } catch (Exception failure) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static String readHead(InputStream input) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int next = input.read();
            if (next < 0) throw new IOException("closed during handshake");
            head.write(next);
            matched = (next == '\r' && (matched == 0 || matched == 2)) || (next == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        return head.toString(StandardCharsets.US_ASCII);
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Session session : all) session.drop();
    }

    /** One accepted connection. */
    static final class Session {
        private final Socket socket;
        private final OutputStream output;
        private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
        private volatile boolean closed;

        private Session(Socket socket) throws IOException {
            this.socket = socket;
            this.output = socket.getOutputStream();
        }

        private void readLoop() {
            try {
                DataInputStream input = new DataInputStream(socket.getInputStream());
                ByteArrayOutputStream message = new ByteArrayOutputStream();
                while (true) {
                    int first = input.readUnsignedByte(), second = input.readUnsignedByte();
                    boolean fin = (first & 0x80) != 0;
                    int opcode = first & 0x0f;
                    long length = second & 0x7f;
                    if (length == 126) length = input.readUnsignedShort();
                    else if (length == 127) length = input.readLong();
                    byte[] mask = new byte[4];
                    if ((second & 0x80) != 0) input.readFully(mask);
                    byte[] payload = new byte[(int) length];
                    input.readFully(payload);
                    for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
                    if (opcode == 0x8) {
                        writeFrame(0x8, new byte[0]);
                        break;
                    }
                    if (opcode == 0x9) {
                        writeFrame(0xA, payload);
                        continue;
                    }
                    if (opcode == 0xA) continue;
                    message.write(payload);
                    if (fin) {
                        received.add(message.toString(StandardCharsets.UTF_8));
                        message.reset();
                    }
                }
            } catch (IOException ended) {
                // The socket closed.
            } finally {
                closed = true;
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        synchronized void writeFrame(int opcode, byte[] payload) throws IOException {
            output.write(0x80 | opcode);
            if (payload.length < 126) output.write(payload.length);
            else if (payload.length < 65536) {
                output.write(126);
                output.write(payload.length >>> 8);
                output.write(payload.length & 0xff);
            } else {
                output.write(127);
                long length = payload.length;
                for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (length >>> shift) & 0xff);
            }
            output.write(payload);
            output.flush();
        }

        void sendText(String text) throws IOException { writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8)); }

        void send(JsonObject frame) throws IOException { sendText(GSON.toJson(frame)); }

        void ping() throws IOException { writeFrame(0x9, new byte[0]); }

        JsonObject next(Duration timeout) throws InterruptedException, IOException {
            String text = received.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (text == null) throw new AssertionError("no frame within " + timeout);
            return StrictJson.object(text);
        }

        boolean awaitClosed(Duration timeout) throws InterruptedException {
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!closed && System.nanoTime() < deadline) Thread.sleep(10);
            return closed;
        }

        void drop() {
            try { socket.close(); } catch (IOException ignored) { }
        }

        /** Runs the challenge and checks the signature as the relay does; returns the register frame. */
        JsonObject register(EditorProduct product, String nonce) throws Exception {
            JsonObject challenge = new JsonObject();
            challenge.addProperty("type", "challenge");
            challenge.addProperty("protocol", 1);
            challenge.addProperty("nonce", nonce);
            send(challenge);
            JsonObject register = next(Duration.ofSeconds(5));
            String serverKey = register.get("serverKey").getAsString();
            if (!EditorFixtures.verifies(serverKey, product.relayChallengePrefix() + nonce, register.get("signature").getAsString()))
                throw new AssertionError("signature does not verify");
            JsonObject registered = new JsonObject();
            registered.addProperty("type", "registered");
            registered.addProperty("fingerprint", PairingCode.fingerprint(Base64.getUrlDecoder().decode(serverKey)));
            send(registered);
            return register;
        }
    }
}
