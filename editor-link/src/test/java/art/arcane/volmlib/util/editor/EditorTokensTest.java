package art.arcane.volmlib.util.editor;

import static art.arcane.volmlib.util.editor.EditorFixtures.ANIMUS;
import static art.arcane.volmlib.util.editor.EditorFixtures.GLOSS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.volmlib.util.editor.EditorFixtures.JsonFormat;
import art.arcane.volmlib.util.editor.EditorFixtures.MutableClock;
import art.arcane.volmlib.util.editor.EditorFixtures.TestWrite;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class EditorTokensTest {
    private static final String SHOWN = "state/editor/tokens.json";

    @TempDir Path directory;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T08:30:05Z"));
    private final TestWrite write = new TestWrite();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    private Path file() { return directory.resolve("tokens.json"); }

    private EditorTokens<String> tokens(EditorProduct product) throws IOException {
        return new EditorTokens<>(product, file(), SHOWN, new JsonFormat(), write, clock, warnings::add);
    }

    private EditorTokens<String> tokens() throws IOException { return tokens(ANIMUS); }

    private static EditorTokens.Grant<String> grant(Duration lifetime, String... scopes) {
        return new EditorTokens.Grant<>("laptop", Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")), List.of(scopes), List.of("shop"), lifetime);
    }

    private static EditorTokens.Grant<String> grant() { return grant(Duration.ofDays(7), "read", "publish"); }

    private static String issue(EditorTokens<String> tokens, EditorTokens.Grant<String> grant) throws Exception {
        EditorTokens.Pairing<String> pairing = tokens.pair(grant);
        return tokens.exchange(pairing.tokenId(), pairing.secret(), Optional.of("Firefox")).getKey();
    }

    private static String id(String text) { return text.substring(4, text.indexOf('.')); }

    private static EditorRefusal.Code refused(Executable call) { return assertThrows(EditorRefusal.class, call).code(); }

    @Test
    void exchange_pairedLink_returnsPrefixedTokenAndStoresOnlyItsHash() throws Exception {
        for (EditorProduct product : List.of(ANIMUS, GLOSS)) {
            EditorTokens<String> tokens = tokens(product);
            EditorTokens.Pairing<String> pairing = tokens.pair(grant());
            Map.Entry<String, EditorTokens.Token<String>> issued = tokens.exchange(pairing.tokenId(), pairing.secret(), Optional.of("Firefox"));
            String text = issued.getKey();
            assertTrue(text.matches(product.tokenPrefix() + "tok_[A-Za-z0-9_-]{16}\\.[A-Za-z0-9_-]{43}"), text);
            assertTrue(text.startsWith(product.tokenPrefix() + pairing.tokenId() + "."), text);
            assertEquals(EditorTokens.sha256(text), issued.getValue().hash());
            assertEquals(List.of("read", "publish"), issued.getValue().scopes());
            assertEquals(Optional.of("Firefox"), issued.getValue().client());
            String stored = Files.readString(file());
            assertFalse(stored.contains(text));
            assertFalse(stored.contains(text.substring(text.indexOf('.') + 1)));
            assertFalse(stored.contains(pairing.secret()));
            assertTrue(stored.contains(EditorTokens.sha256(text)));
            assertEquals(pairing.tokenId(), tokens.authenticate(text).id());
            Files.delete(file());
        }
    }

    @Test
    void exchange_secondTime_answersPairingUsed() throws Exception {
        EditorTokens<String> tokens = tokens();
        EditorTokens.Pairing<String> pairing = tokens.pair(grant());
        tokens.exchange(pairing.tokenId(), pairing.secret(), Optional.empty());
        EditorRefusal again = assertThrows(EditorRefusal.class, () -> tokens.exchange(pairing.tokenId(), pairing.secret(), Optional.empty()));
        assertEquals(EditorRefusal.Code.PAIRING_USED, again.code());
        assertTrue(again.getMessage().contains("/animus editor pair"), again.getMessage());
    }

    @Test
    void exchange_revokedWaitingLink_answersPairingRevoked() throws Exception {
        EditorTokens<String> tokens = tokens();
        EditorTokens.Pairing<String> pairing = tokens.pair(grant());
        assertEquals(1, tokens.pendingPairings());
        assertEquals(List.of(pairing.tokenId()), tokens.revoke(pairing.tokenId()));
        assertEquals(0, tokens.pendingPairings());
        assertEquals(EditorRefusal.Code.PAIRING_REVOKED, refused(() -> tokens.exchange(pairing.tokenId(), pairing.secret(), Optional.empty())));
    }

    @Test
    void exchange_wrongSecret_answersPairingInvalidForOpenUsedAndRevokedLinks() throws Exception {
        EditorTokens<String> tokens = tokens();
        String wrong = "B".repeat(43);
        EditorTokens.Pairing<String> open = tokens.pair(grant()), used = tokens.pair(grant()), revoked = tokens.pair(grant());
        tokens.exchange(used.tokenId(), used.secret(), Optional.empty());
        tokens.revoke(revoked.tokenId());
        for (EditorTokens.Pairing<String> pairing : List.of(open, used, revoked))
            assertEquals(EditorRefusal.Code.PAIRING_INVALID, refused(() -> tokens.exchange(pairing.tokenId(), wrong, Optional.empty())), pairing.tokenId());
        assertEquals(EditorRefusal.Code.PAIRING_INVALID, refused(() -> tokens.exchange("tok_unknownunknown", wrong, Optional.empty())));
        tokens.exchange(open.tokenId(), open.secret(), Optional.empty());
    }

    @Test
    void exchange_expiredLink_answersPairingExpired() throws Exception {
        EditorTokens<String> tokens = tokens();
        EditorTokens.Pairing<String> late = tokens.pair(grant()), used = tokens.pair(grant());
        tokens.exchange(used.tokenId(), used.secret(), Optional.empty());
        clock.advance(EditorTokens.PAIRING_LIFETIME);
        assertEquals(EditorRefusal.Code.PAIRING_EXPIRED, refused(() -> tokens.exchange(late.tokenId(), late.secret(), Optional.empty())));
        assertEquals(EditorRefusal.Code.PAIRING_EXPIRED, refused(() -> tokens.exchange(late.tokenId(), late.secret(), Optional.empty())));
        assertEquals(EditorRefusal.Code.PAIRING_EXPIRED, refused(() -> tokens.exchange(used.tokenId(), used.secret(), Optional.empty())));
        assertEquals(1, tokens.list().size());
    }

    @Test
    void authenticate_use_slidesExpiryAndWritesOnlyPastTheThreshold() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant());
        Instant issued = clock.instant();
        assertEquals(1, write.writes.get());
        clock.advance(Duration.ofMinutes(30));
        assertEquals(clock.instant().plus(Duration.ofDays(7)), tokens.authenticate(text).expires());
        assertEquals(Optional.of(clock.instant()), tokens.find(id(text)).orElseThrow().lastUsed());
        assertEquals(1, write.writes.get());
        assertEquals(issued.plus(Duration.ofDays(7)), tokens(ANIMUS).find(id(text)).orElseThrow().expires());
        clock.advance(EditorTokens.SLIDE_WRITE);
        Instant slid = tokens.authenticate(text).expires();
        assertEquals(2, write.writes.get());
        assertEquals(slid, tokens(ANIMUS).find(id(text)).orElseThrow().expires());
        clock.advance(Duration.ofMinutes(1));
        tokens.authenticate(text);
        assertEquals(2, write.writes.get());
    }

    @Test
    void authenticate_shortLifetime_writesAfterAQuarterOfIt() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant(Duration.ofMinutes(20), "read"));
        clock.advance(Duration.ofMinutes(4));
        tokens.authenticate(text);
        assertEquals(1, write.writes.get());
        clock.advance(Duration.ofMinutes(2));
        Instant slid = tokens.authenticate(text).expires();
        assertEquals(2, write.writes.get());
        assertEquals(slid, tokens(ANIMUS).find(id(text)).orElseThrow().expires());
    }

    @Test
    void authenticate_malformedOrForeignValues_unauthorized() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant());
        String forged = text.substring(0, text.indexOf('.') + 1) + "C".repeat(43);
        for (String value : Arrays.asList(null, "", "anm_x", forged, "gls_" + text.substring(4), text + "x"))
            assertEquals(EditorRefusal.Code.UNAUTHORIZED, refused(() -> tokens.authenticate(value)), String.valueOf(value));
    }

    @Test
    void advance_replayedCounter_refusedWithTheStoredCounter() throws Exception {
        EditorTokens<String> tokens = tokens();
        String id = id(issue(tokens, grant()));
        tokens.advance(id, 5);
        for (long counter : new long[]{5, 3, 0}) {
            EditorRefusal replayed = assertThrows(EditorRefusal.class, () -> tokens.advance(id, counter));
            assertEquals(EditorRefusal.Code.COUNTER_REPLAYED, replayed.code());
            assertEquals(5, replayed.extra().get("counter").getAsLong());
            assertEquals("X-Animus-Counter must exceed 5.", replayed.getMessage());
            JsonObject error = replayed.response().body().orElseThrow().getAsJsonObject().getAsJsonObject("error");
            assertEquals(409, replayed.response().status());
            assertEquals("counter-replayed", error.get("code").getAsString());
            assertEquals(5, error.get("counter").getAsLong());
        }
        tokens.advance(id, 6);
        assertEquals(6, tokens(ANIMUS).find(id).orElseThrow().counter());
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.advance("tok_unknownunknown", 1)));
    }

    @Test
    void dropScope_lastScope_leavesTheTokenWithNone() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant());
        String id = id(text);
        assertTrue(tokens.dropScope(id, "publish"));
        assertEquals(List.of("read"), tokens.find(id).orElseThrow().scopes());
        assertFalse(tokens.dropScope(id, "publish"));
        assertTrue(tokens.dropScope(id, "read"));
        assertEquals(List.of(), tokens.find(id).orElseThrow().scopes());
        assertFalse(tokens.dropScope("tok_unknownunknown", "read"));
        assertFalse(tokens.authenticate(text).has("read"));
        assertEquals(List.of(), tokens(ANIMUS).find(id).orElseThrow().scopes());
    }

    @Test
    void revoke_all_endsEveryTokenAndWaitingLink() throws Exception {
        EditorTokens<String> tokens = tokens();
        String first = issue(tokens, grant()), second = issue(tokens, grant());
        EditorTokens.Pairing<String> waiting = tokens.pair(grant());
        assertEquals(List.of(id(first), id(second), waiting.tokenId()), tokens.revoke("all"));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.authenticate(first)));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.authenticate(second)));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.stillValid(id(first))));
        assertEquals(EditorRefusal.Code.PAIRING_REVOKED, refused(() -> tokens.exchange(waiting.tokenId(), waiting.secret(), Optional.empty())));
        assertEquals(List.of(), tokens.list());
        assertEquals(0, tokens.pendingPairings());
        assertEquals(List.of(), tokens.revoke("tok_unknownunknown"));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens(ANIMUS).authenticate(first)));
    }

    @Test
    void expire_pastExpiry_endsTokensAsExpiredAndKeepsRevokedOnesRevoked() throws Exception {
        EditorTokens<String> tokens = tokens();
        String shortLived = issue(tokens, grant(Duration.ofHours(1), "read")), kept = issue(tokens, grant()), revoked = issue(tokens, grant());
        tokens.revoke(id(revoked));
        clock.advance(Duration.ofHours(1));
        assertEquals(EditorRefusal.Code.TOKEN_EXPIRED, refused(() -> tokens.stillValid(id(shortLived))));
        assertEquals(List.of(id(shortLived)), tokens.expire());
        assertEquals(List.of(), tokens.expire());
        assertEquals(EditorRefusal.Code.TOKEN_EXPIRED, refused(() -> tokens.authenticate(shortLived)));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.authenticate(revoked)));
        tokens.authenticate(kept);
        EditorTokens<String> reloaded = tokens(ANIMUS);
        assertEquals(EditorRefusal.Code.TOKEN_EXPIRED, refused(() -> reloaded.authenticate(shortLived)));
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> reloaded.authenticate(revoked)));
        clock.advance(Duration.ofDays(1).plusSeconds(1));
        tokens.expire();
        assertEquals(EditorRefusal.Code.UNAUTHORIZED, refused(() -> tokens.authenticate(shortLived)));
    }

    @Test
    void reload_writtenFile_keepsTokens() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant());
        EditorTokens.Token<String> before = tokens.find(id(text)).orElseThrow();
        EditorTokens<String> reloaded = tokens(ANIMUS);
        assertEquals(List.of(before), reloaded.list());
        EditorTokens.Token<String> used = reloaded.authenticate(text);
        assertEquals("laptop", used.label());
        assertEquals(List.of("read", "publish"), used.scopes());
        assertEquals(List.of("shop"), used.folders());
        assertTrue(used.reaches("shop/sub"));
        assertFalse(used.reaches("shopping"));
        assertEquals(List.of(), warnings);
    }

    @Test
    void load_damagedFile_movedAsideWithAWarningAndAnEmptyStore() throws Exception {
        String torn = "{\"tokens\":[{\"id\":\"tok_";
        Files.writeString(file(), torn);
        EditorTokens<String> tokens = tokens();
        Path aside = directory.resolve("tokens.damaged-20261119-083005.json");
        assertFalse(Files.exists(file()));
        assertEquals(torn, Files.readString(aside));
        assertEquals(List.of(), tokens.list());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().startsWith(SHOWN + " is damaged ("), warnings.getFirst());
        assertTrue(warnings.getFirst().contains("moved it to state/editor/tokens.damaged-20261119-083005.json"), warnings.getFirst());
        Files.write(file(), new byte[]{(byte) 0xff, (byte) 0xfe, '{', '}'});
        tokens();
        assertTrue(Files.exists(directory.resolve("tokens.damaged-20261119-083005-2.json")));
        assertTrue(warnings.get(1).contains("(not UTF-8)"), warnings.get(1));
        issue(tokens, grant());
        assertEquals(1, tokens(ANIMUS).list().size());
        try (Stream<Path> files = Files.list(directory)) { assertEquals(3, files.count()); }
    }

    @Test
    void revoke_failingWrite_throwsUnsavedAndTheNextExpireWritesAgain() throws Exception {
        EditorTokens<String> tokens = tokens();
        String text = issue(tokens, grant());
        write.failing = true;
        EditorTokens.Unsaved unsaved = assertThrows(EditorTokens.Unsaved.class, () -> tokens.revoke(id(text)));
        assertEquals(List.of(id(text)), unsaved.ids());
        assertTrue(unsaved.getMessage().startsWith(SHOWN + " could not be written (disk full)"), unsaved.getMessage());
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens.authenticate(text)));
        write.failing = false;
        tokens(ANIMUS).authenticate(text);
        int writes = write.writes.get();
        assertEquals(List.of(), tokens.expire());
        assertEquals(writes + 1, write.writes.get());
        assertEquals(EditorRefusal.Code.TOKEN_REVOKED, refused(() -> tokens(ANIMUS).authenticate(text)));
        tokens.expire();
        assertEquals(writes + 1, write.writes.get());
    }

    @Test
    void advance_blockedWrite_doesNotHoldUpAnotherTokensAuthenticate() throws Exception {
        EditorTokens<String> tokens = tokens();
        String first = issue(tokens, grant()), second = issue(tokens, grant());
        CountDownLatch entered = write.block();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> advancing = CompletableFuture.runAsync(() -> {
                try { tokens.advance(id(first), 1); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            }, pool);
            assertTrue(entered.await(5, TimeUnit.SECONDS), "the write started");
            CompletableFuture<EditorTokens.Token<String>> other = CompletableFuture.supplyAsync(() -> {
                try { return tokens.authenticate(second); }
                catch (EditorRefusal refusal) { throw new IllegalStateException(refusal); }
            }, pool);
            assertEquals(id(second), other.get(5, TimeUnit.SECONDS).id());
            assertEquals(2, tokens.list().size());
            assertFalse(advancing.isDone());
            write.unblock();
            advancing.get(5, TimeUnit.SECONDS);
        } finally {
            write.unblock();
            pool.shutdown();
        }
        assertEquals(1, tokens(ANIMUS).find(id(first)).orElseThrow().counter());
    }

    @Test
    void grant_noScopesOrLifetimeOutOfRange_refused() {
        assertThrows(IllegalArgumentException.class, () -> grant(Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class, () -> grant(Duration.ofSeconds(30), "read"));
        assertThrows(IllegalArgumentException.class, () -> grant(Duration.ofDays(366), "read"));
    }

    @Test
    void pair_waitingLinkLimit_refused() throws Exception {
        EditorTokens<String> tokens = tokens();
        for (int index = 0; index < EditorTokens.MAXIMUM_PAIRINGS; index++) tokens.pair(grant());
        IllegalStateException full = assertThrows(IllegalStateException.class, () -> tokens.pair(grant()));
        assertTrue(full.getMessage().startsWith("Too many pairing links are waiting"), full.getMessage());
    }
}
