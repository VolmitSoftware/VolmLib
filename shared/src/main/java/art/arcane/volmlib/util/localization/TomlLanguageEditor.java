package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public final class TomlLanguageEditor {
    private TomlLanguageEditor() {
    }

    public static EditResult upsertText(String raw, String key, String value) throws IOException {
        return upsert(raw, key, new TextValue(value));
    }

    public static EditResult upsert(String raw, String key, MessageValue value) throws IOException {
        String source = Objects.requireNonNullElse(raw, "");
        String requiredKey = LocalizationSupport.requireMessageId(key);
        MessageValue requiredValue = Objects.requireNonNull(value, "Language value cannot be null");
        JsonObject updated = parse(source);
        upsertValue(updated, requiredKey, serializeValue(requiredValue));
        return serialize(updated, leadingCommentBlock(source));
    }

    private static JsonElement serializeValue(MessageValue value) {
        if (value instanceof TextValue text) {
            return new JsonPrimitive(text.template());
        }
        if (value instanceof LinesValue lines) {
            JsonArray array = new JsonArray();
            for (String line : lines.lines()) {
                array.add(line);
            }
            return array;
        }
        PluralValue plural = (PluralValue) value;
        JsonObject object = new JsonObject();
        for (Map.Entry<String, String> form : plural.forms().entrySet()) {
            object.addProperty(form.getKey(), form.getValue());
        }
        return object;
    }

    public static EditResult remove(String raw, String key) throws IOException {
        String source = Objects.requireNonNullElse(raw, "");
        String requiredKey = LocalizationSupport.requireMessageId(key);
        JsonObject updated = parse(source);
        remove(updated, requiredKey);
        return serialize(updated, leadingCommentBlock(source));
    }

    private static JsonObject parse(String raw) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(Objects.requireNonNullElse(raw, ""));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Language file is not a TOML table");
        }
        return parsed.getAsJsonObject();
    }

    private static void upsertValue(JsonObject root, String key, JsonElement value) throws IOException {
        int separator = key.indexOf('.');
        if (root.has(key) || separator < 0) {
            root.add(key, value);
            return;
        }
        String segment = key.substring(0, separator);
        JsonElement child = root.get(segment);
        if (child == null) {
            child = new JsonObject();
            root.add(segment, child);
        }
        if (!child.isJsonObject()) {
            throw new IOException("Language key collides with a non-table value: " + segment);
        }
        upsertValue(child.getAsJsonObject(), key.substring(separator + 1), value);
    }

    private static boolean remove(JsonObject object, String key) throws IOException {
        int separator = key.indexOf('.');
        if (object.has(key) || separator < 0) {
            object.remove(key);
            return object.size() == 0;
        }
        String segment = key.substring(0, separator);
        JsonElement child = object.get(segment);
        if (child == null) {
            return object.size() == 0;
        }
        if (!child.isJsonObject()) {
            throw new IOException("Language key collides with a non-table value: " + segment);
        }
        if (remove(child.getAsJsonObject(), key.substring(separator + 1))) {
            object.remove(segment);
        }
        return object.size() == 0;
    }

    private static EditResult serialize(JsonObject updated, String leadingComments) throws IOException {
        String serialized = TomlCodec.toToml(updated);
        JsonObject roundTripped = parse(serialized);
        if (!roundTripped.equals(updated)) {
            throw new IOException("Language file could not be serialized without changing its values");
        }
        String content = leadingComments.isEmpty() ? serialized : leadingComments + serialized;
        return new EditResult(content, updated.size() == 0);
    }

    private static String leadingCommentBlock(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder comments = new StringBuilder();
        boolean foundComment = false;
        for (String line : lines) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                comments.append(line).append('\n');
                foundComment = true;
                continue;
            }
            if (line.isBlank() && foundComment) {
                comments.append('\n');
                continue;
            }
            break;
        }
        if (!foundComment) {
            return "";
        }
        while (comments.length() > 1
                && comments.charAt(comments.length() - 1) == '\n'
                && comments.charAt(comments.length() - 2) == '\n') {
            comments.setLength(comments.length() - 1);
        }
        return comments.append('\n').toString();
    }

    public record EditResult(String content, boolean empty) {
        public EditResult {
            content = Objects.requireNonNull(content, "Language content cannot be null");
        }
    }
}
