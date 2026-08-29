package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.Objects;

public final class TomlLanguageEditor {
    private TomlLanguageEditor() {
    }

    public static EditResult upsertText(String raw, String key, String value) throws IOException {
        String source = Objects.requireNonNullElse(raw, "");
        String requiredKey = LocalizationSupport.requireMessageId(key);
        String requiredValue = Objects.requireNonNull(value, "Language value cannot be null");
        JsonObject root = parse(source);
        JsonObject updated = root.deepCopy();
        String[] path = requiredKey.split("\\.");
        JsonObject parent = requireParent(updated, path, true);
        parent.add(path[path.length - 1], new JsonPrimitive(requiredValue));
        return serialize(updated, leadingCommentBlock(source));
    }

    public static EditResult remove(String raw, String key) throws IOException {
        String source = Objects.requireNonNullElse(raw, "");
        String requiredKey = LocalizationSupport.requireMessageId(key);
        JsonObject root = parse(source);
        JsonObject updated = root.deepCopy();
        String[] path = requiredKey.split("\\.");
        remove(updated, path, 0);
        return serialize(updated, leadingCommentBlock(source));
    }

    private static JsonObject parse(String raw) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(Objects.requireNonNullElse(raw, ""));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Language file is not a TOML table");
        }
        return parsed.getAsJsonObject();
    }

    private static JsonObject requireParent(JsonObject root, String[] path, boolean create) throws IOException {
        JsonObject cursor = root;
        for (int index = 0; index < path.length - 1; index++) {
            String segment = path[index];
            JsonElement child = cursor.get(segment);
            if (child == null) {
                if (!create) {
                    return null;
                }
                JsonObject created = new JsonObject();
                cursor.add(segment, created);
                cursor = created;
                continue;
            }
            if (!child.isJsonObject()) {
                throw new IOException("Language key collides with a non-table value: " + segment);
            }
            cursor = child.getAsJsonObject();
        }
        return cursor;
    }

    private static boolean remove(JsonObject object, String[] path, int index) throws IOException {
        String segment = path[index];
        if (index == path.length - 1) {
            object.remove(segment);
            return object.size() == 0;
        }
        JsonElement child = object.get(segment);
        if (child == null) {
            return object.size() == 0;
        }
        if (!child.isJsonObject()) {
            throw new IOException("Language key collides with a non-table value: " + segment);
        }
        if (remove(child.getAsJsonObject(), path, index + 1)) {
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
