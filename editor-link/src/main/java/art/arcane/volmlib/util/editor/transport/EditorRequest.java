package art.arcane.volmlib.util.editor.transport;

import com.google.gson.JsonElement;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One editor API call, independent of how it arrived. {@code path} starts with {@code /v1/} and keeps its query
 * string; {@code headers} match names case-insensitively. Over the relay, {@code Origin} and {@code X-Forwarded-For} are
 * written by the relay from the browser's connection.
 */
public record EditorRequest(Transport transport, String method, String path, Map<String, String> headers, Optional<JsonElement> body) {
    public enum Transport { DIRECT, RELAY }

    public EditorRequest {
        if (!path.startsWith("/v1/")) throw new IllegalArgumentException("path must start with /v1/");
        TreeMap<String, String> folded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        folded.putAll(headers);
        headers = Collections.unmodifiableMap(folded);
        method = method.toUpperCase(Locale.ROOT);
    }

    public Optional<String> header(String name) { return Optional.ofNullable(headers.get(name)); }

    /** The path without its query string. */
    public String route() {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }
}
