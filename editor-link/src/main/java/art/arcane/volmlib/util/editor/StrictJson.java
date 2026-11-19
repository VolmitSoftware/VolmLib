package art.arcane.volmlib.util.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

/**
 * Strict JSON for everything the link reads from outside: no lenient syntax, no duplicate keys (Gson's tree parser keeps
 * the last one), at most 64 levels deep, and nothing after the one object.
 */
public final class StrictJson {
    public static final int MAX_DEPTH = 64;

    private StrictJson() {}

    /** Exactly one JSON object. */
    public static JsonObject object(String text) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement root = value(reader, 0);
            if (!root.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("$: expected exactly one JSON object");
            return root.getAsJsonObject();
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            throw new IOException("Invalid JSON: " + invalid.getMessage(), invalid);
        }
    }

    private static JsonElement value(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException(reader.getPath() + ": nesting exceeds " + MAX_DEPTH);
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IOException(reader.getPath() + ": duplicate key");
                    object.add(key, value(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(value(reader, depth + 1));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IOException(reader.getPath() + ": expected a JSON value");
        };
    }
}
