package art.arcane.volmlib.util.localization;

import art.arcane.volmlib.util.config.TomlCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    public static Map<String, String> parseValidText(String raw, MessageCatalog catalog) throws IOException {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, MessageValue> entry : parseValidValues(raw, catalog).entrySet()) {
            if (entry.getValue() instanceof TextValue text) {
                values.put(entry.getKey(), text.template());
            }
        }
        return values;
    }

    public static Map<String, MessageValue> parseValidValues(String raw, MessageCatalog catalog) throws IOException {
        MessageCatalog requiredCatalog = Objects.requireNonNull(catalog, "catalog");
        JsonElement parsed = parseRoot(raw);
        LocaleOverlay.Builder candidate = LocaleOverlay.builder(requiredCatalog.englishLocale());
        Map<String, JsonElement> flattened = flattenedValues(parsed.getAsJsonObject());
        for (MessageKey definition : requiredCatalog.keys()) {
            JsonElement value = flattened.get(definition.id());
            if (value == null && definition instanceof PluralKey) {
                value = pluralForms(flattened, definition.id(), requiredCatalog);
            }
            MessageValue translation = parseValue(definition, value);
            if (translation != null) {
                candidate.put(definition.id(), translation);
            }
        }
        return LocalizationValidator.validValues(requiredCatalog, candidate.build()).values();
    }

    private static Map<String, JsonElement> flattenedValues(JsonObject source) {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        appendFlattened(values, new HashSet<>(), source, "");
        return values;
    }

    private static void appendFlattened(Map<String, JsonElement> values, Set<String> ambiguous,
                                        JsonObject source, String prefix) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && value.getAsJsonObject().size() > 0) {
                appendFlattened(values, ambiguous, value.getAsJsonObject(), key);
            } else if (!ambiguous.contains(key) && values.putIfAbsent(key, value) != null) {
                values.remove(key);
                ambiguous.add(key);
            }
        }
    }

    private static JsonObject pluralForms(Map<String, JsonElement> flattened, String key, MessageCatalog catalog) {
        String prefix = key + ".";
        JsonObject forms = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : flattened.entrySet()) {
            if (entry.getKey().startsWith(prefix) && catalog.key(entry.getKey()) == null) {
                forms.add(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return forms;
    }

    private static MessageValue parseValue(MessageKey definition, JsonElement value) {
        if (definition == null || value == null || value.isJsonNull()) {
            return null;
        }
        try {
            if (definition instanceof TextKey && isText(value)) {
                return new TextValue(value.getAsString());
            }
            if (definition instanceof LinesKey && value.isJsonArray()) {
                ArrayList<String> lines = new ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    if (!isText(element)) {
                        return null;
                    }
                    lines.add(element.getAsString());
                }
                return new LinesValue(lines);
            }
            if (definition instanceof PluralKey && value.isJsonObject()) {
                LinkedHashMap<String, String> forms = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    if (!isText(entry.getValue())) {
                        return null;
                    }
                    forms.put(entry.getKey(), entry.getValue().getAsString());
                }
                return new PluralValue(forms);
            }
        } catch (IllegalArgumentException invalidTranslation) {
            return null;
        }
        return null;
    }

    private static boolean isText(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
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
            if (value != null && value.isJsonObject() && hasAcceptedChild) {
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
