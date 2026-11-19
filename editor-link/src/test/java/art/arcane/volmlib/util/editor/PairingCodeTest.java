package art.arcane.volmlib.util.editor;

import static art.arcane.volmlib.util.editor.EditorFixtures.ANIMUS;
import static art.arcane.volmlib.util.editor.EditorFixtures.GLOSS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PairingCodeTest {
    private static final String TOKEN_ID = "tok_Ab3dEf6hIj9kLm2n", SECRET = "A".repeat(43);
    private static final Instant EXPIRES = Instant.parse("2026-11-19T10:10:00Z");
    private final EditorIdentity identity = EditorIdentity.generate();

    private PairingCode animus(Optional<String> direct, Optional<String> relay) {
        return new PairingCode(ANIMUS, direct, relay, identity.fingerprint(), identity.serverKey(), TOKEN_ID, SECRET, EXPIRES, Optional.of("laptop"), Optional.of("Survival"));
    }

    private PairingCode gloss(List<String> scopes, List<String> folders) {
        return new PairingCode(GLOSS, Optional.of("http://127.0.0.1:9799/gloss"), Optional.of("wss://relay.example.com"), identity.fingerprint(), identity.serverKey(),
                TOKEN_ID, SECRET, EXPIRES, Optional.empty(), Optional.empty(), scopes, folders);
    }

    private static JsonObject payload(EditorProduct product, String code) throws Exception {
        return StrictJson.object(new String(Base64.getUrlDecoder().decode(code.substring(product.pairingPrefix().length())), StandardCharsets.UTF_8));
    }

    private static String code(EditorProduct product, String json) {
        return product.pairingPrefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String refusal(EditorProduct product, String code) {
        return assertThrows(IllegalArgumentException.class, () -> PairingCode.decode(product, code)).getMessage();
    }

    @Test
    void roundTrip_animus_reencodesByteIdentical() throws Exception {
        PairingCode code = animus(Optional.of("http://127.0.0.1:9797/animus"), Optional.of("wss://relay.example.com"));
        String encoded = code.encode();
        PairingCode decoded = PairingCode.decode(ANIMUS, encoded);
        assertTrue(encoded.startsWith("ANM1."), encoded);
        assertEquals(code, decoded);
        assertArrayEquals(encoded.getBytes(StandardCharsets.US_ASCII), decoded.encode().getBytes(StandardCharsets.US_ASCII));
        assertEquals(List.of("v", "directUrl", "relayUrl", "fingerprint", "serverKey", "tokenId", "secret", "expiresAt", "label", "serverName"),
                List.copyOf(payload(ANIMUS, encoded).keySet()));
    }

    @Test
    void roundTrip_gloss_carriesScopesAndFoldersLast() throws Exception {
        PairingCode code = gloss(List.of("read", "publish:world"), List.of("shop", "shop/sub"));
        String encoded = code.encode();
        PairingCode decoded = PairingCode.decode(GLOSS, encoded);
        assertTrue(encoded.startsWith("GLS1."), encoded);
        assertEquals(code, decoded);
        assertEquals(encoded, decoded.encode());
        assertEquals(List.of("read", "publish:world"), decoded.scopes());
        assertEquals(List.of("shop", "shop/sub"), decoded.folders());
        assertEquals(List.of("v", "directUrl", "relayUrl", "fingerprint", "serverKey", "tokenId", "secret", "expiresAt", "scopes", "folders"),
                List.copyOf(payload(GLOSS, encoded).keySet()));
    }

    @Test
    void encode_glossWithoutFolders_leavesFoldersOut() throws Exception {
        String encoded = gloss(List.of("read"), List.of()).encode();
        assertEquals(List.of("v", "directUrl", "relayUrl", "fingerprint", "serverKey", "tokenId", "secret", "expiresAt", "scopes"), List.copyOf(payload(GLOSS, encoded).keySet()));
        assertEquals(List.of(), PairingCode.decode(GLOSS, encoded).folders());
    }

    @Test
    void grant_gloss_requiresScopes() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> gloss(List.of(), List.of("shop")));
        JsonObject payload = payload(GLOSS, gloss(List.of("read"), List.of()).encode());
        payload.remove("scopes");
        assertEquals("$.scopes: at least one scope", refusal(GLOSS, code(GLOSS, payload.toString())));
        payload.add("scopes", new JsonArray());
        assertEquals("$.scopes: at least one scope", refusal(GLOSS, code(GLOSS, payload.toString())));
        payload.addProperty("scopes", "read");
        assertEquals("$.scopes: expected an array of strings", refusal(GLOSS, code(GLOSS, payload.toString())));
    }

    @Test
    void grant_animus_refusesScopesAndFolders() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new PairingCode(ANIMUS, Optional.of("https://mc.example.com"), Optional.empty(), identity.fingerprint(),
                identity.serverKey(), TOKEN_ID, SECRET, EXPIRES, Optional.empty(), Optional.empty(), List.of("read"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new PairingCode(ANIMUS, Optional.of("https://mc.example.com"), Optional.empty(), identity.fingerprint(),
                identity.serverKey(), TOKEN_ID, SECRET, EXPIRES, Optional.empty(), Optional.empty(), List.of(), List.of("shop")));
        JsonObject payload = payload(ANIMUS, animus(Optional.of("https://mc.example.com"), Optional.empty()).encode());
        payload.add("scopes", new JsonArray());
        assertEquals("$.scopes: unknown field", refusal(ANIMUS, code(ANIMUS, payload.toString())));
    }

    @Test
    void decode_unknownField_refused() throws Exception {
        JsonObject payload = payload(ANIMUS, animus(Optional.of("https://mc.example.com"), Optional.empty()).encode());
        payload.addProperty("extra", 1);
        assertEquals("$.extra: unknown field", refusal(ANIMUS, code(ANIMUS, payload.toString())));
    }

    @Test
    void decode_duplicateKey_refused() throws Exception {
        String json = payload(ANIMUS, animus(Optional.of("https://mc.example.com"), Optional.empty()).encode()).toString();
        String repeated = "{\"tokenId\":\"tok_Zz9yXw8vUt7sRq6p\"," + json.substring(1);
        assertEquals("$: the pairing payload is not one JSON object", refusal(ANIMUS, code(ANIMUS, repeated)));
    }

    @Test
    void decode_malformedCodes_refused() throws Exception {
        String valid = animus(Optional.of("https://mc.example.com"), Optional.empty()).encode();
        refusal(GLOSS, valid);
        refusal(ANIMUS, "ANM1.");
        refusal(ANIMUS, "ANM1.e30=");
        refusal(ANIMUS, valid + "+");
        refusal(ANIMUS, "ANM1." + "A".repeat(PairingCode.MAXIMUM_CODE_LENGTH));
        refusal(ANIMUS, null);
        refusal(ANIMUS, code(ANIMUS, "[]"));
        JsonObject payload = payload(ANIMUS, valid);
        payload.addProperty("v", 2);
        assertEquals("$.v: expected 1", refusal(ANIMUS, code(ANIMUS, payload.toString())));
        payload.addProperty("v", 1);
        payload.addProperty("expiresAt", "tomorrow");
        assertEquals("$.expiresAt: expected an ISO-8601 instant", refusal(ANIMUS, code(ANIMUS, payload.toString())));
        payload.addProperty("expiresAt", EXPIRES.toString());
        payload.addProperty("fingerprint", EditorIdentity.generate().fingerprint());
        assertEquals("$.fingerprint: does not match serverKey", refusal(ANIMUS, code(ANIMUS, payload.toString())));
    }

    @Test
    void urls_schemeRules_enforced() {
        for (String direct : List.of("https://mc.example.com", "https://mc.example.com:9797/animus", "http://127.0.0.1:9797", "http://localhost:9797", "HTTP://LOCALHOST"))
            animus(Optional.of(direct), Optional.empty());
        for (String direct : List.of("http://10.0.0.5:9797", "http://mc.example.com", "ftp://mc.example.com", "https://user@mc.example.com", "mc.example.com", "https://"))
            assertThrows(IllegalArgumentException.class, () -> animus(Optional.of(direct), Optional.empty()), direct);
        for (String relay : List.of("wss://relay.example.com", "ws://127.0.0.1:8080", "ws://localhost:8080"))
            animus(Optional.empty(), Optional.of(relay));
        for (String relay : List.of("ws://relay.example.com", "https://relay.example.com", "wss://user@relay.example.com", "relay"))
            assertThrows(IllegalArgumentException.class, () -> animus(Optional.empty(), Optional.of(relay)), relay);
        assertThrows(IllegalArgumentException.class, () -> animus(Optional.empty(), Optional.empty()));
    }
}
