package art.arcane.volmlib.util.editor;

import art.arcane.volmlib.util.editor.transport.EditorResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A request the link refuses before a product's routes see it: an error code shared by every product's editor API, its
 * HTTP status, an optional retry hint and optional extra fields for the error object (such as {@code counter}). Products
 * map it onto their own error type by {@link Code#wireName()}.
 */
public final class EditorRefusal extends Exception {
    public enum Code {
        UNAUTHORIZED("unauthorized", 401), TOKEN_EXPIRED("token-expired", 401), TOKEN_REVOKED("token-revoked", 401),
        COUNTER_REPLAYED("counter-replayed", 409), PAIRING_INVALID("pairing-invalid", 401), PAIRING_USED("pairing-used", 401),
        PAIRING_REVOKED("pairing-revoked", 401), PAIRING_EXPIRED("pairing-expired", 410), NOT_FOUND("not-found", 404),
        INVALID_REQUEST("invalid-request", 400), TOO_LARGE("too-large", 413), RATE_LIMITED("rate-limited", 429), BUSY("busy", 503),
        UNSUPPORTED_PROTOCOL("unsupported-protocol", 400), INTERNAL("internal", 500);

        private final String wireName;
        private final int status;

        Code(String wireName, int status) {
            this.wireName = wireName;
            this.status = status;
        }

        public String wireName() { return wireName; }

        public int status() { return status; }
    }

    private final Code code;
    private final long retryAfterMillis;
    private final JsonObject extra;

    public EditorRefusal(Code code, String message) { this(code, message, -1, null); }

    /** {@code retryAfterMillis} is negative for none; {@code extra} holds fields added to the error object, or null. */
    public EditorRefusal(Code code, String message, long retryAfterMillis, JsonObject extra) {
        super(Objects.requireNonNull(message), null, false, false);
        this.code = Objects.requireNonNull(code);
        this.retryAfterMillis = retryAfterMillis;
        this.extra = extra == null ? null : extra.deepCopy();
    }

    public Code code() { return code; }

    public long retryAfterMillis() { return retryAfterMillis; }

    /** A copy of the extra fields, or null. */
    public JsonObject extra() { return extra == null ? null : extra.deepCopy(); }

    /** The error response: {@code {"error": {code, message, retryAfterMillis?, extra...}}} and a {@code Retry-After} header with a hint. */
    public EditorResponse response() {
        JsonObject detail = new JsonObject();
        detail.addProperty("code", code.wireName());
        String message = getMessage().isBlank() ? code.wireName() : getMessage();
        detail.addProperty("message", message.length() > 2048 ? message.substring(0, 2048) : message);
        if (retryAfterMillis >= 0) detail.addProperty("retryAfterMillis", retryAfterMillis);
        if (extra != null) for (Map.Entry<String, JsonElement> field : extra.entrySet()) detail.add(field.getKey(), field.getValue().deepCopy());
        JsonObject body = new JsonObject();
        body.add("error", detail);
        Map<String, String> headers = retryAfterMillis >= 0 ? Map.of("Retry-After", Long.toString(Math.max(1, (retryAfterMillis + 999) / 1000))) : Map.of();
        return new EditorResponse(code.status(), headers, Optional.of(body));
    }
}
