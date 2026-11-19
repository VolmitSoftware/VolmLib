package art.arcane.volmlib.util.editor.transport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Optional;

/** The answer to an {@link EditorRequest}: an HTTP status, optional headers and an optional JSON body. */
public record EditorResponse(int status, Map<String, String> headers, Optional<JsonElement> body) {
    public EditorResponse {
        if (status < 100 || status > 599) throw new IllegalArgumentException("status must be 100..599");
        headers = Map.copyOf(headers);
    }

    public static EditorResponse json(int status, JsonElement body) { return new EditorResponse(status, Map.of(), Optional.of(body)); }

    /** An {@code error-response} body with one of the schema's error codes. */
    public static EditorResponse error(int status, String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject body = new JsonObject();
        body.add("error", error);
        return json(status, body);
    }
}
