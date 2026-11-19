package art.arcane.volmlib.util.editor;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Editor tokens of one product, with scopes of type {@code S}. Only a SHA-256 of each token is stored, with its label,
 * issuing player, scopes, folders, expiry, lifetime, last use and last accepted request counter. Expiry slides: each use
 * moves it to one lifetime after that use. Pairing secrets live in memory only, and a link that was opened or revoked is
 * remembered until it would have expired, so its browser learns which.
 *
 * <p>The product keeps the file's location and layout ({@link Format}) and writes it ({@link DurableWrite}). Changes are made
 * under this object's lock and written after it is released, so a slow disk holds up only the request that changed
 * something; a write that fails is tried again by {@link #expire}. A damaged file is moved aside with one warning and the
 * editor starts with no tokens.
 */
public final class EditorTokens<S> {
    public static final int MAXIMUM_TOKENS = 256, MAXIMUM_PAIRINGS = 64;
    public static final Duration PAIRING_LIFETIME = Duration.ofMinutes(10);
    /**
     * A slid expiry is written once it moved this far past the stored one, or a quarter of the token's lifetime when that is
     * shorter, so use does not write on every request.
     */
    static final Duration SLIDE_WRITE = Duration.ofHours(1);
    private static final int MAXIMUM_STORE_BYTES = 16 * 1024 * 1024;
    private static final DateTimeFormatter DAMAGED = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    /**
     * A stored token. {@code admin} is empty for tokens paired from the console; {@code adminChecked} is when the server last
     * confirmed that the player may still pair, and pairing counts as one.
     */
    public record Token<S>(String id, String hash, String label, Optional<UUID> admin, List<S> scopes, List<String> folders, Instant created,
                           Instant expires, Duration lifetime, Optional<Instant> lastUsed, long counter, Optional<String> client, Instant adminChecked) {
        public Token {
            scopes = List.copyOf(scopes);
            folders = List.copyOf(folders);
        }

        private Token<S> used(Instant now, Instant until) { return new Token<>(id, hash, label, admin, scopes, folders, created, until, lifetime, Optional.of(now), counter, client, adminChecked); }

        private Token<S> counted(long value) { return new Token<>(id, hash, label, admin, scopes, folders, created, expires, lifetime, lastUsed, value, client, adminChecked); }

        private Token<S> checked(Instant now) { return new Token<>(id, hash, label, admin, scopes, folders, created, expires, lifetime, lastUsed, counter, client, now); }

        private Token<S> scoped(List<S> kept) { return new Token<>(id, hash, label, admin, kept, folders, created, expires, lifetime, lastUsed, counter, client, adminChecked); }

        public boolean has(S scope) { return scopes.contains(scope); }

        /** True when the token reaches {@code folder}: one of its folders or one inside it. A token without folders reaches every folder. */
        public boolean reaches(String folder) { return folders.isEmpty() || folders.stream().anyMatch((String allowed) -> folder.equals(allowed) || folder.startsWith(allowed + "/")); }
    }

    /** What a pair command asked for. The product checks folder names before it builds one. */
    public record Grant<S>(String label, Optional<UUID> admin, List<S> scopes, List<String> folders, Duration lifetime) {
        public Grant {
            if (label.length() > 64) throw new IllegalArgumentException("label: at most 64 characters");
            if (scopes.isEmpty()) throw new IllegalArgumentException("scopes: at least one scope");
            if (folders.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("folders: a folder name is empty");
            if (lifetime.compareTo(Duration.ofMinutes(1)) < 0 || lifetime.compareTo(Duration.ofDays(365)) > 0) throw new IllegalArgumentException("expires: 1 minute to 365 days");
            scopes = List.copyOf(scopes);
            folders = List.copyOf(folders);
        }
    }

    /** A pairing waiting for its browser. */
    public record Pairing<S>(String tokenId, String secret, Instant expires, Grant<S> grant) {}

    /** A pairing link not opened yet, without its secret. */
    public record Open<S>(String tokenId, Instant expires, Grant<S> grant) {}

    /** A token that ended, remembered for a day after its expiry so its browser hears why: {@code expired}, else revoked. */
    public record Ended(String id, Instant expires, boolean expired) {}

    /** What the file holds. */
    public record Stored<S>(List<Token<S>> tokens, List<Ended> ended) {
        public Stored {
            tokens = List.copyOf(tokens);
            ended = List.copyOf(ended);
        }
    }

    /** The product's file layout. {@code decode} throws for a damaged file; anything it throws moves the file aside. */
    public interface Format<S> {
        Stored<S> decode(String json) throws IOException;

        String encode(Stored<S> stored);
    }

    /** A revoke that holds in memory but did not reach the disk; {@link #expire} writes it again. */
    public static final class Unsaved extends IOException {
        private final List<String> ids;

        Unsaved(String file, List<String> ids, IOException cause) {
            super(file + " could not be written (" + cause.getMessage() + ")", cause);
            this.ids = List.copyOf(ids);
        }

        public List<String> ids() { return ids; }
    }

    private record Pending<S>(String secretHash, Instant expires, Grant<S> grant) {}

    /** A link that was opened or revoked, kept until it would have expired so its browser learns which. */
    private record Closed(String secretHash, Instant expires, boolean revoked) {}

    /** The store's content at one moment; the number keeps an older one from replacing a newer one on disk. */
    private record Snapshot(long version, byte[] bytes) {}

    private final EditorProduct product;
    private final Path file;
    private final String shownAs;
    private final Format<S> format;
    private final DurableWrite durable;
    private final Clock clock;
    private final Consumer<String> warn;
    private final Pattern bearer;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Token<S>> tokens = new LinkedHashMap<>();
    private final Map<String, Ended> ended = new LinkedHashMap<>();
    private final Map<String, Pending<S>> pairings = new LinkedHashMap<>();
    private final Map<String, Closed> closed = new LinkedHashMap<>();
    /** Each token's expiry as last handed to the disk. */
    private final Map<String, Instant> stored = new HashMap<>();
    private final Object writing = new Object();
    private long version;
    /** The last write failed, so the next {@link #expire} writes again. */
    private boolean unsaved;
    /** Guarded by {@link #writing}: the newest snapshot on disk. */
    private long written;

    /** {@code shownAs} names the file in messages, such as {@code state/editor/tokens.json}. */
    public EditorTokens(EditorProduct product, Path file, String shownAs, Format<S> format, DurableWrite durable, Clock clock, Consumer<String> warn) throws IOException {
        this.product = Objects.requireNonNull(product);
        this.file = Objects.requireNonNull(file);
        this.shownAs = Objects.requireNonNull(shownAs);
        this.format = Objects.requireNonNull(format);
        this.durable = Objects.requireNonNull(durable);
        this.clock = Objects.requireNonNull(clock);
        this.warn = Objects.requireNonNull(warn);
        this.bearer = Pattern.compile(Pattern.quote(product.tokenPrefix()) + "tok_[A-Za-z0-9_-]{8,60}\\.[A-Za-z0-9_-]{43}");
        load();
    }

    /** Starts a pairing; the code carries the secret, which is good once for ten minutes. */
    public synchronized Pairing<S> pair(Grant<S> grant) {
        prunePairings();
        if (pendingPairings() >= MAXIMUM_PAIRINGS || pairings.size() >= MAXIMUM_PAIRINGS * 4)
            throw new IllegalStateException("Too many pairing links are waiting; let some expire or revoke them first");
        if (tokens.size() >= MAXIMUM_TOKENS) throw new IllegalStateException("Token limit of " + MAXIMUM_TOKENS + " reached; revoke unused tokens first");
        String id = "tok_" + randomText(12);
        String secret = randomText(32);
        Instant expires = now().plus(PAIRING_LIFETIME);
        pairings.put(id, new Pending<>(sha256(secret), expires, grant));
        return new Pairing<>(id, secret, expires, grant);
    }

    /** Exchanges a pairing secret for a token. Returns the token text, which is shown once and never stored. */
    public Map.Entry<String, Token<S>> exchange(String tokenId, String secret, Optional<String> client) throws EditorRefusal, IOException {
        String text;
        Token<S> token;
        Snapshot snapshot;
        synchronized (this) {
            Pending<S> pending = pairings.get(tokenId);
            if (pending == null) {
                Closed done = closed.get(tokenId);
                // Only the browser holding the secret learns that the link was used or revoked.
                if (done == null || !sameHash(done.secretHash(), secret)) throw refusal(EditorRefusal.Code.PAIRING_INVALID, "This pairing link is not known to the server.");
                if (!now().isBefore(done.expires())) throw refusal(EditorRefusal.Code.PAIRING_EXPIRED, "This pairing link expired; run " + product.pairCommand() + " again.");
                if (done.revoked()) throw refusal(EditorRefusal.Code.PAIRING_REVOKED, "This pairing link was revoked; run " + product.pairCommand() + " again.");
                throw refusal(EditorRefusal.Code.PAIRING_USED, "This pairing link was already used; run " + product.pairCommand() + " again.");
            }
            if (!sameHash(pending.secretHash(), secret)) throw refusal(EditorRefusal.Code.PAIRING_INVALID, "This pairing link is not known to the server.");
            pairings.remove(tokenId);
            close(tokenId, pending, false);
            if (!now().isBefore(pending.expires())) throw refusal(EditorRefusal.Code.PAIRING_EXPIRED, "This pairing link expired; run " + product.pairCommand() + " again.");
            text = product.tokenPrefix() + tokenId + "." + randomText(32);
            Instant created = now();
            Grant<S> grant = pending.grant();
            token = new Token<>(tokenId, sha256(text), grant.label(), grant.admin(), grant.scopes(), grant.folders(), created, created.plus(grant.lifetime()),
                    grant.lifetime(), Optional.empty(), 0, client.map((String name) -> name.length() > 128 ? name.substring(0, 128) : name), created);
            tokens.put(tokenId, token);
            snapshot = snapshot();
        }
        write(snapshot);
        return Map.entry(text, token);
    }

    /** The token a bearer value names, or why it is refused. Constant-time hash comparison. Slides the expiry. */
    public Token<S> authenticate(String bearerValue) throws EditorRefusal {
        if (bearerValue == null || !bearer.matcher(bearerValue).matches()) throw refusal(EditorRefusal.Code.UNAUTHORIZED, "A valid editor token is required.");
        String id = bearerValue.substring(product.tokenPrefix().length(), bearerValue.indexOf('.'));
        Token<S> used;
        Snapshot snapshot = null;
        synchronized (this) {
            Ended gone = ended.get(id);
            if (gone != null) throw gone.expired() ? expiredToken() : revokedToken();
            Token<S> token = tokens.get(id);
            if (token == null || !MessageDigest.isEqual(token.hash().getBytes(StandardCharsets.US_ASCII), sha256(bearerValue).getBytes(StandardCharsets.US_ASCII)))
                throw refusal(EditorRefusal.Code.UNAUTHORIZED, "A valid editor token is required.");
            Instant now = now();
            if (!now.isBefore(token.expires())) throw expiredToken();
            Instant slid = now.plus(token.lifetime());
            used = token.used(now, slid.isAfter(token.expires()) ? slid : token.expires());
            tokens.put(id, used);
            Duration step = token.lifetime().dividedBy(4);
            if (step.compareTo(SLIDE_WRITE) > 0) step = SLIDE_WRITE;
            if (slid.isAfter(stored.getOrDefault(id, token.expires()).plus(step))) snapshot = snapshot();
        }
        if (snapshot != null) {
            try { write(snapshot); }
            catch (IOException failure) { warn.accept("Editor could not write " + shownAs + " (" + failure.getMessage() + ") with the new expiry of " + id + "; it is written again on the next change"); }
        }
        return used;
    }

    /** Refuses a token revoked or expired since its request was authenticated, as work queued for it starts. */
    public synchronized void stillValid(String tokenId) throws EditorRefusal {
        Token<S> token = tokens.get(tokenId);
        if (token == null) throw revokedToken();
        if (!now().isBefore(token.expires())) throw expiredToken();
    }

    /** Accepts a counted request's counter, which must exceed every earlier one for the token, and stores it before the request runs. */
    public void advance(String tokenId, long counter) throws EditorRefusal, IOException {
        Snapshot snapshot;
        synchronized (this) {
            Token<S> token = tokens.get(tokenId);
            if (token == null) throw revokedToken();
            if (counter <= token.counter()) {
                JsonObject extra = new JsonObject();
                extra.addProperty("counter", token.counter());
                throw new EditorRefusal(EditorRefusal.Code.COUNTER_REPLAYED, product.counterHeader() + " must exceed " + token.counter() + ".", -1, extra);
            }
            tokens.put(tokenId, token.counted(counter));
            snapshot = snapshot();
        }
        write(snapshot);
    }

    /** Records that the token's player may still pair. Writes at most once an hour per token. */
    public void confirmAdmin(String tokenId) throws IOException {
        Snapshot snapshot;
        synchronized (this) {
            Token<S> token = tokens.get(tokenId);
            if (token == null || token.adminChecked().plus(Duration.ofHours(1)).isAfter(now())) return;
            tokens.put(tokenId, token.checked(now()));
            snapshot = snapshot();
        }
        write(snapshot);
    }

    /** Takes one scope away from a token, keeping the rest, even when none is left; false when the token is gone or never had it. */
    public boolean dropScope(String tokenId, S scope) throws IOException {
        Snapshot snapshot;
        synchronized (this) {
            Token<S> token = tokens.get(tokenId);
            if (token == null || !token.has(scope)) return false;
            tokens.put(tokenId, token.scoped(token.scopes().stream().filter((S held) -> !held.equals(scope)).toList()));
            snapshot = snapshot();
        }
        write(snapshot);
        return true;
    }

    /**
     * Revokes one token, or every token for {@code all}; links not opened yet are cancelled too, so their browsers can no
     * longer pair. Returns the ids. The revoke holds at once; when it cannot be written, {@link Unsaved} names the ids and
     * {@link #expire} writes again.
     */
    public List<String> revoke(String idOrAll) throws IOException {
        List<String> revoked;
        Snapshot snapshot = null;
        synchronized (this) {
            boolean all = idOrAll.equals("all");
            List<String> ids = all ? new ArrayList<>(tokens.keySet()) : tokens.containsKey(idOrAll) ? new ArrayList<>(List.of(idOrAll)) : new ArrayList<>();
            Instant now = now();
            for (Iterator<Map.Entry<String, Pending<S>>> open = pairings.entrySet().iterator(); open.hasNext(); ) {
                Map.Entry<String, Pending<S>> pairing = open.next();
                if (!all && !pairing.getKey().equals(idOrAll)) continue;
                open.remove();
                close(pairing.getKey(), pairing.getValue(), true);
                if (now.isBefore(pairing.getValue().expires())) ids.add(pairing.getKey());
            }
            boolean changed = false;
            for (String id : ids) {
                Token<S> token = tokens.remove(id);
                if (token == null) continue;
                ended.put(id, new Ended(id, token.expires(), false));
                changed = true;
            }
            if (changed) snapshot = snapshot();
            revoked = List.copyOf(ids);
        }
        if (snapshot != null) {
            try { write(snapshot); }
            catch (IOException failure) { throw new Unsaved(shownAs, revoked, failure); }
        }
        return revoked;
    }

    /**
     * Ends tokens past their expiry and forgets old ended ones. Returns the ids that expired now. Also writes again what an
     * earlier write could not; that retry fails quietly, since the first failure was already reported.
     */
    public List<String> expire() throws IOException {
        List<String> expired = new ArrayList<>();
        Snapshot snapshot = null;
        boolean retryOnly;
        synchronized (this) {
            Instant now = now();
            for (Token<S> token : tokens.values()) if (!now.isBefore(token.expires())) expired.add(token.id());
            boolean changed = !expired.isEmpty();
            for (String id : expired) ended.put(id, new Ended(id, tokens.remove(id).expires(), true));
            Iterator<Ended> old = ended.values().iterator();
            while (old.hasNext()) {
                if (old.next().expires().plus(Duration.ofDays(1)).isBefore(now)) {
                    old.remove();
                    changed = true;
                }
            }
            prunePairings();
            retryOnly = !changed;
            if (changed || unsaved) snapshot = snapshot();
        }
        if (snapshot != null) {
            try { write(snapshot); }
            catch (IOException failure) { if (!retryOnly) throw failure; }
        }
        return expired;
    }

    public synchronized List<Token<S>> list() { return List.copyOf(tokens.values()); }

    public synchronized Optional<Token<S>> find(String id) { return Optional.ofNullable(tokens.get(id)); }

    public synchronized int pendingPairings() { return openPairings().size(); }

    /** Pairing links not opened yet and not expired. */
    public synchronized List<Open<S>> openPairings() {
        prunePairings();
        Instant now = now();
        List<Open<S>> open = new ArrayList<>();
        pairings.forEach((String id, Pending<S> pending) -> { if (now.isBefore(pending.expires())) open.add(new Open<>(id, pending.expires(), pending.grant())); });
        return open;
    }

    /** The waiting pairing with this id, expired or not, until it is opened or pruned. */
    public synchronized Optional<Open<S>> pairing(String tokenId) {
        Pending<S> pending = pairings.get(tokenId);
        return pending == null ? Optional.empty() : Optional.of(new Open<>(tokenId, pending.expires(), pending.grant()));
    }

    private void prunePairings() {
        Instant now = now();
        // Expired and closed links stay an hour so a late browser learns what happened instead of that the link never existed.
        pairings.values().removeIf((Pending<S> pending) -> now.isAfter(pending.expires().plus(Duration.ofHours(1))));
        closed.values().removeIf((Closed done) -> now.isAfter(done.expires().plus(Duration.ofHours(1))));
    }

    private void close(String tokenId, Pending<S> pending, boolean revoked) {
        closed.put(tokenId, new Closed(pending.secretHash(), pending.expires(), revoked));
        Iterator<String> oldest = closed.keySet().iterator();
        while (closed.size() > MAXIMUM_PAIRINGS * 4 && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
    }

    private EditorRefusal expiredToken() { return refusal(EditorRefusal.Code.TOKEN_EXPIRED, "This token expired; pair the browser again."); }

    private EditorRefusal revokedToken() { return refusal(EditorRefusal.Code.TOKEN_REVOKED, "This token was revoked."); }

    private static EditorRefusal refusal(EditorRefusal.Code code, String message) { return new EditorRefusal(code, message); }

    private static boolean sameHash(String secretHash, String secret) {
        return MessageDigest.isEqual(secretHash.getBytes(StandardCharsets.US_ASCII), sha256(secret).getBytes(StandardCharsets.US_ASCII));
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.SECONDS); }

    private String randomText(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Lower-case hex SHA-256 of the UTF-8 text, as tokens are stored. */
    public static String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException(missing); }
    }

    /** Reads the store; a damaged one is moved aside with one warning, and the editor starts with no tokens. */
    private void load() throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return;
        byte[] bytes;
        try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(MAXIMUM_STORE_BYTES + 1); }
        try {
            if (bytes.length > MAXIMUM_STORE_BYTES) throw new IOException("larger than " + MAXIMUM_STORE_BYTES + " bytes");
            Stored<S> saved = format.decode(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            for (Token<S> token : saved.tokens()) tokens.put(token.id(), token);
            for (Ended end : saved.ended()) ended.put(end.id(), end);
        } catch (IOException | RuntimeException damaged) {
            tokens.clear();
            ended.clear();
            String why = damaged instanceof CharacterCodingException ? "not UTF-8" : damaged.getMessage() == null ? damaged.getClass().getSimpleName()
                    : damaged.getMessage().lines().findFirst().orElse("");
            Path aside = quarantine();
            int slash = shownAs.lastIndexOf('/');
            warn.accept(shownAs + " is damaged (" + why + "); moved it to " + shownAs.substring(0, slash + 1) + aside.getFileName()
                    + " and started with no paired browsers, so each one pairs again");
        }
        tokens.forEach((String id, Token<S> token) -> stored.put(id, token.expires()));
    }

    /** Moves the damaged store to {@code <name>.damaged-<UTC time>.json} beside it, where it stays for inspection. */
    private Path quarantine() throws IOException {
        String name = file.getFileName().toString();
        String stem = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        String stamp = DAMAGED.format(clock.instant());
        Path aside = file.resolveSibling(stem + ".damaged-" + stamp + ".json");
        for (int copy = 2; Files.exists(aside, LinkOption.NOFOLLOW_LINKS); copy++) aside = file.resolveSibling(stem + ".damaged-" + stamp + "-" + copy + ".json");
        Files.move(file, aside, StandardCopyOption.ATOMIC_MOVE);
        return aside;
    }

    /** The store as it is now, numbered; called holding this object's lock. */
    private Snapshot snapshot() {
        List<Ended> gone = List.copyOf(ended.values());
        String encoded = format.encode(new Stored<>(List.copyOf(tokens.values()), gone));
        stored.clear();
        tokens.forEach((String id, Token<S> token) -> stored.put(id, token.expires()));
        return new Snapshot(++version, encoded.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes the snapshot durably unless a newer one is on disk already, which holds this one's changes too. Called without this object's lock. */
    private void write(Snapshot snapshot) throws IOException {
        synchronized (writing) {
            if (snapshot.version() <= written) return;
            try { durable.replace(file, snapshot.bytes()); }
            catch (IOException | RuntimeException failure) {
                synchronized (this) { unsaved = true; }
                throw failure instanceof IOException io ? io : new IOException(failure.getMessage(), failure);
            }
            written = snapshot.version();
        }
        synchronized (this) {
            if (version == snapshot.version()) unsaved = false;
        }
    }
}
