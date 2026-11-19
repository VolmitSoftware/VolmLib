package art.arcane.volmlib.util.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * The server's Ed25519 identity, kept in a JSON file ({@code v}, {@code seed}, {@code serverKey}, {@code fingerprint}). The
 * relay registers the server under {@link #fingerprint()}, and pairing codes and {@code GET /v1/hello} carry the same key
 * and fingerprint.
 */
public final class EditorIdentity {
    private static final int VERSION = 1, KEY_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] seed;
    private final PrivateKey privateKey;
    private final String serverKey;
    private final String fingerprint;

    private EditorIdentity(byte[] seed, KeyPair pair) {
        this.seed = seed.clone();
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] rawPublicKey = Arrays.copyOfRange(encoded, encoded.length - KEY_BYTES, encoded.length);
        this.privateKey = pair.getPrivate();
        this.serverKey = ENCODER.encodeToString(rawPublicKey);
        this.fingerprint = PairingCode.fingerprint(rawPublicKey);
    }

    /** The identity whose private key is the 32-byte Ed25519 seed. */
    public static EditorIdentity fromSeed(byte[] seed) {
        if (seed.length != KEY_BYTES) throw new IllegalArgumentException("an Ed25519 seed is 32 bytes");
        byte[] copy = seed.clone();
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            generator.initialize(NamedParameterSpec.ED25519, new SeedRandom(copy));
            return new EditorIdentity(copy, generator.generateKeyPair());
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("Ed25519 is unavailable", missing);
        }
    }

    public static EditorIdentity generate() {
        byte[] seed = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(seed);
        return fromSeed(seed);
    }

    /**
     * Reads the identity in {@code file}, creating it on first use with {@code write} and leaving it readable by the owner
     * only. Refuses a file whose key does not match its seed. {@code shownAs} names the file in messages.
     */
    public static EditorIdentity loadOrCreate(Path file, String shownAs, DurableWrite write) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            EditorIdentity created = generate();
            JsonObject document = new JsonObject();
            document.addProperty("v", VERSION);
            document.addProperty("seed", ENCODER.encodeToString(created.seed));
            document.addProperty("serverKey", created.serverKey);
            document.addProperty("fingerprint", created.fingerprint);
            write.replace(file, (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(document) + "\n").getBytes(StandardCharsets.UTF_8));
            ownerOnly(file);
            return created;
        }
        try { return read(file, shownAs); }
        catch (IOException | RuntimeException damaged) {
            String reason = damaged.getMessage() == null ? damaged.getClass().getSimpleName() : damaged.getMessage().replace(shownAs + ": ", "");
            throw new IOException(shownAs + " is damaged (" + reason + "); delete it and restart to create a new editor identity, then pair the editor again", damaged);
        }
    }

    private static EditorIdentity read(Path file, String shownAs) throws IOException {
        JsonObject document = StrictJson.object(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString());
        JsonElement version = document.get("v"), seedText = document.get("seed"), key = document.get("serverKey");
        if (version == null || !version.isJsonPrimitive() || version.getAsInt() != VERSION || seedText == null || !seedText.isJsonPrimitive() || key == null || !key.isJsonPrimitive())
            throw new IOException(shownAs + ": expected v, seed and serverKey");
        byte[] seed;
        try { seed = DECODER.decode(seedText.getAsString()); }
        catch (IllegalArgumentException invalid) { throw new IOException(shownAs + ": seed is not base64url", invalid); }
        if (seed.length != KEY_BYTES) throw new IOException(shownAs + ": seed must be 32 bytes");
        EditorIdentity identity = fromSeed(seed);
        if (!identity.serverKey.equals(key.getAsString())) throw new IOException(shownAs + ": serverKey does not match seed");
        return identity;
    }

    private static void ownerOnly(Path file) throws IOException {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) return;
        Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(file, permissions);
    }

    /** Raw 32-byte public key in base64url without padding, as in pairing codes and relay registration. */
    public String serverKey() { return serverKey; }

    /** {@code sha256:<hex>} of the raw public key. */
    public String fingerprint() { return fingerprint; }

    /** Ed25519 signature of the UTF-8 text, base64url without padding. */
    public String sign(String text) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Ed25519 signing failed", failure);
        }
    }

    /** Feeds the seed to the key generator, which draws exactly the 32 private-key bytes. */
    private static final class SeedRandom extends SecureRandom {
        private final byte[] seed;

        private SeedRandom(byte[] seed) {
            super();
            this.seed = seed;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (bytes.length != seed.length) throw new IllegalStateException("unexpected key generator draw of " + bytes.length + " bytes");
            System.arraycopy(seed, 0, bytes, 0, bytes.length);
        }
    }
}
