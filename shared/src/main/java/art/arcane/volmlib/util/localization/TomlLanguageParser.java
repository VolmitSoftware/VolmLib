package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class TomlLanguageParser {
    private TomlLanguageParser() {
    }

    public static Map<String, String> parseText(String raw) throws IOException {
        JsonElement parsed = parseRoot(raw);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        appendText(values, parsed.getAsJsonObject(), "");
        return values;
    }

    public static Map<String, String> parseText(String raw, Set<String> acceptedKeys) throws IOException {
        JsonElement parsed = parseRoot(raw);
        Set<String> requiredKeys = Set.copyOf(acceptedKeys);
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        appendAcceptedText(values, parsed.getAsJsonObject(), "", requiredKeys);
        return values;
    }

    private static JsonElement parseRoot(String raw) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(raw);
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Language source is not a TOML table");
        }
        return parsed;
    }

    private static void appendText(Map<String, String> values, JsonObject object, String prefix) throws IOException {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject()) {
                appendText(values, value.getAsJsonObject(), key);
                continue;
            }
            values.put(key, requireText(key, value));
        }
    }

    private static void appendAcceptedText(
            Map<String, String> values,
            JsonObject object,
            String prefix,
            Set<String> acceptedKeys
    ) throws IOException {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            boolean accepted = acceptedKeys.contains(key);
            boolean hasAcceptedChild = hasAcceptedChild(acceptedKeys, key);
            if (!accepted && !hasAcceptedChild) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonObject() && hasAcceptedChild && !accepted) {
                appendAcceptedText(values, value.getAsJsonObject(), key, acceptedKeys);
                continue;
            }
            if (accepted) {
                values.put(key, requireText(key, value));
            }
        }
    }

    private static String requireText(String key, JsonElement value) throws IOException {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            throw new IOException("Language value must be text: " + key);
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) {
            throw new IOException("Language value must be text: " + key);
        }
        return primitive.getAsString();
    }

    private static boolean hasAcceptedChild(Set<String> acceptedKeys, String key) {
        String prefix = key + ".";
        for (String acceptedKey : acceptedKeys) {
            if (acceptedKey.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
