package art.arcane.volmlib.util.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Products, a settable clock, a JSON token layout and a controllable durable write shared by the editor-link tests. */
public final class EditorFixtures {
    public static final EditorProduct ANIMUS = new EditorProduct("Animus", "anm_", "ANM1.", "/animus editor pair", false);
    public static final EditorProduct GLOSS = new EditorProduct("Gloss", "gls_", "GLS1.", "/gloss editor pair", true);
    private static final byte[] ED25519_SPKI_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    private EditorFixtures() {}

    /** Ed25519 check of {@code signature} (base64url) over {@code text} with a raw base64url public key. */
    public static boolean verifies(String serverKey, String text, String signature) throws Exception {
        byte[] raw = Base64.getUrlDecoder().decode(serverKey);
        byte[] spki = new byte[ED25519_SPKI_PREFIX.length + raw.length];
        System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, ED25519_SPKI_PREFIX.length);
        System.arraycopy(raw, 0, spki, ED25519_SPKI_PREFIX.length, raw.length);
        PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(text.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getUrlDecoder().decode(signature));
    }

    public static final class MutableClock extends Clock {
        private volatile Instant now;

        public MutableClock(Instant start) { now = start; }

        public void advance(Duration step) { now = now.plus(step); }

        public void set(Instant instant) { now = instant; }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return now; }
    }

    /** Writes with {@link Files#write}; fails while {@link #failing} and, once {@link #block} is armed, waits inside the write. */
    public static final class TestWrite implements DurableWrite {
        public final AtomicInteger writes = new AtomicInteger();
        public volatile boolean failing;
        private volatile CountDownLatch entered;
        private volatile CountDownLatch release;

        /** The next write signals the returned latch on entry and waits until {@link #unblock}. */
        public CountDownLatch block() {
            CountDownLatch started = new CountDownLatch(1);
            entered = started;
            release = new CountDownLatch(1);
            return started;
        }

        public void unblock() {
            CountDownLatch gate = release;
            if (gate != null) gate.countDown();
        }

        @Override
        public void replace(Path file, byte[] content) throws IOException {
            if (failing) throw new IOException("disk full");
            CountDownLatch gate = release;
            if (gate != null) {
                entered.countDown();
                try {
                    if (!gate.await(20, TimeUnit.SECONDS)) throw new IOException("never released");
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", stopped);
                }
                release = null;
            }
            Files.write(file, content);
            writes.incrementAndGet();
        }
    }

    /** The token store as plain JSON with String scopes. */
    public static final class JsonFormat implements EditorTokens.Format<String> {
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        @Override
        public EditorTokens.Stored<String> decode(String json) throws IOException {
            JsonObject root = StrictJson.object(json);
            List<EditorTokens.Token<String>> tokens = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("tokens")) {
                JsonObject token = element.getAsJsonObject();
                tokens.add(new EditorTokens.Token<>(token.get("id").getAsString(), token.get("hash").getAsString(), token.get("label").getAsString(),
                        optional(token, "admin").map(UUID::fromString), strings(token.getAsJsonArray("scopes")), strings(token.getAsJsonArray("folders")),
                        Instant.parse(token.get("created").getAsString()), Instant.parse(token.get("expires").getAsString()),
                        Duration.ofSeconds(token.get("lifetimeSeconds").getAsLong()), optional(token, "lastUsed").map(Instant::parse),
                        token.get("counter").getAsLong(), optional(token, "client"), Instant.parse(token.get("adminChecked").getAsString())));
            }
            List<EditorTokens.Ended> ended = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("ended")) {
                JsonObject end = element.getAsJsonObject();
                ended.add(new EditorTokens.Ended(end.get("id").getAsString(), Instant.parse(end.get("expires").getAsString()), end.get("expired").getAsBoolean()));
            }
            return new EditorTokens.Stored<>(tokens, ended);
        }

        @Override
        public String encode(EditorTokens.Stored<String> stored) {
            JsonArray tokens = new JsonArray();
            for (EditorTokens.Token<String> token : stored.tokens()) {
                JsonObject object = new JsonObject();
                object.addProperty("id", token.id());
                object.addProperty("hash", token.hash());
                object.addProperty("label", token.label());
                token.admin().ifPresent((UUID admin) -> object.addProperty("admin", admin.toString()));
                object.add("scopes", array(token.scopes()));
                object.add("folders", array(token.folders()));
                object.addProperty("created", token.created().toString());
                object.addProperty("expires", token.expires().toString());
                object.addProperty("lifetimeSeconds", token.lifetime().toSeconds());
                token.lastUsed().ifPresent((Instant used) -> object.addProperty("lastUsed", used.toString()));
                object.addProperty("counter", token.counter());
                token.client().ifPresent((String client) -> object.addProperty("client", client));
                object.addProperty("adminChecked", token.adminChecked().toString());
                tokens.add(object);
            }
            JsonArray ended = new JsonArray();
            for (EditorTokens.Ended end : stored.ended()) {
                JsonObject object = new JsonObject();
                object.addProperty("id", end.id());
                object.addProperty("expires", end.expires().toString());
                object.addProperty("expired", end.expired());
                ended.add(object);
            }
            JsonObject root = new JsonObject();
            root.add("tokens", tokens);
            root.add("ended", ended);
            return GSON.toJson(root);
        }

        private static Optional<String> optional(JsonObject object, String key) {
            return object.has(key) ? Optional.of(object.get(key).getAsString()) : Optional.empty();
        }

        private static List<String> strings(JsonArray array) {
            List<String> values = new ArrayList<>();
            for (JsonElement element : array) values.add(element.getAsString());
            return values;
        }

        private static JsonArray array(List<String> values) {
            JsonArray array = new JsonArray();
            values.forEach(array::add);
            return array;
        }
    }
}
