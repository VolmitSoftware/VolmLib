package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
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
        LinkedHashMap<String, JsonElement> updated = TomlLanguageWriter.flattenedValues(parse(source));
        if (requiredValue instanceof PluralValue) {
            updated.keySet().removeIf(existing -> existing.startsWith(requiredKey + "."));
        }
        updated.put(requiredKey, TomlLanguageWriter.serializeValue(requiredValue));
        return serialize(updated, leadingCommentBlock(source));
    }

    public static EditResult remove(String raw, String key) throws IOException {
        String source = Objects.requireNonNullElse(raw, "");
        String requiredKey = LocalizationSupport.requireMessageId(key);
        LinkedHashMap<String, JsonElement> updated = TomlLanguageWriter.flattenedValues(parse(source));
        if (updated.remove(requiredKey) == null) {
            updated.keySet().removeIf(existing -> existing.startsWith(requiredKey + "."));
        }
        return serialize(updated, leadingCommentBlock(source));
    }

    private static JsonObject parse(String raw) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(Objects.requireNonNullElse(raw, ""));
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Language file is not a TOML table");
        }
        return parsed.getAsJsonObject();
    }

    private static EditResult serialize(Map<String, JsonElement> values, String leadingComments) throws IOException {
        JsonObject updated = new JsonObject();
        values.forEach(updated::add);
        String serialized = TomlLanguageWriter.renderJson(updated, List.of());
        JsonObject roundTripped = parse(serialized);
        if (!TomlLanguageWriter.flattenedValues(roundTripped).equals(TomlLanguageWriter.flattenedValues(updated))) {
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
