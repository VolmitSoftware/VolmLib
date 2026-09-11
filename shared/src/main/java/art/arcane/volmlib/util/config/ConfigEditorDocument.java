package art.arcane.volmlib.util.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConfigEditorDocument {
    private final String source;
    private final JsonObject root;

    private ConfigEditorDocument(String source, JsonObject root) {
        this.source = source;
        this.root = root;
    }

    public static ConfigEditorDocument fromToml(String source) throws IOException {
        String required = Objects.requireNonNull(source, "source");
        JsonElement parsed = TomlCodec.toJsonElement(required);
        if (!parsed.isJsonObject()) {
            throw new IOException("Configuration must be a TOML table");
        }
        return new ConfigEditorDocument(required, parsed.getAsJsonObject());
    }

    public String source() {
        return source;
    }

    public JsonElement value(List<String> path) {
        return resolve(path).deepCopy();
    }

    public List<Entry> entries(List<String> path) {
        List<String> parent = List.copyOf(path);
        JsonElement value = resolve(parent);
        ArrayList<Entry> entries = new ArrayList<>();
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : value.getAsJsonObject().entrySet()) {
                entries.add(entry(parent, child.getKey(), child.getValue()));
            }
        } else if (kind(value) == Kind.TABLE_ARRAY) {
            JsonArray array = value.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                entries.add(entry(parent, Integer.toString(index), array.get(index)));
            }
        } else {
            throw new IllegalArgumentException("Setting is not a section: " + path);
        }
        return List.copyOf(entries);
    }

    public JsonElement parseValue(List<String> path, String input) throws IOException {
        JsonElement current = resolve(path);
        String required = Objects.requireNonNull(input, "input");
        return switch (kind(current)) {
            case TEXT -> new JsonPrimitive(required);
            case BOOLEAN -> parseBoolean(required.strip());
            case INTEGER -> parseInteger(required.strip());
            case DECIMAL -> parseDecimal(required.strip());
            case LIST -> parseList(required, current.getAsJsonArray());
            case TABLE, TABLE_ARRAY -> throw new IllegalArgumentException("Open the section to edit its settings");
        };
    }

    public Edit edit(List<String> path, JsonElement value) {
        return new Edit(this, path, value);
    }

    private JsonElement resolve(List<String> path) {
        JsonElement current = root;
        for (String segment : Objects.requireNonNull(path, "path")) {
            Objects.requireNonNull(segment, "path segment");
            if (current.isJsonObject()) {
                current = current.getAsJsonObject().get(segment);
            } else if (current.isJsonArray() && segment.matches("0|[1-9][0-9]*")) {
                try {
                    int index = Integer.parseInt(segment);
                    JsonArray array = current.getAsJsonArray();
                    current = index < array.size() ? array.get(index) : null;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Unknown setting: " + path, exception);
                }
            } else {
                current = null;
            }
            if (current == null) {
                throw new IllegalArgumentException("Unknown setting: " + path);
            }
        }
        return current;
    }

    private static Entry entry(List<String> parent, String name, JsonElement value) {
        ArrayList<String> path = new ArrayList<>(parent.size() + 1);
        path.addAll(parent);
        path.add(name);
        return new Entry(path, name, kind(value), value);
    }

    private static Kind kind(JsonElement value) {
        if (value.isJsonObject()) {
            return Kind.TABLE;
        }
        if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) {
                    return Kind.TABLE_ARRAY;
                }
            }
            return Kind.LIST;
        }
        if (!value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Configuration values cannot be null");
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return Kind.BOOLEAN;
        }
        if (primitive.isString()) {
            return Kind.TEXT;
        }
        return primitive.getAsString().matches("-?[0-9]+") ? Kind.INTEGER : Kind.DECIMAL;
    }

    private static JsonPrimitive parseBoolean(String input) {
        if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Enter true or false");
        }
        return new JsonPrimitive(Boolean.parseBoolean(input));
    }

    private static JsonPrimitive parseInteger(String input) {
        if (!input.matches("[+-]?[0-9]+")) {
            throw new IllegalArgumentException("Enter a whole number");
        }
        try {
            return new JsonPrimitive(Long.parseLong(input));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number between " + Long.MIN_VALUE + " and " + Long.MAX_VALUE,
                    exception);
        }
    }

    static JsonPrimitive parseDecimal(String input) {
        try {
            double number = Double.parseDouble(input);
            if (!Double.isFinite(number)) {
                throw new IllegalArgumentException("Enter a finite number");
            }
            return new JsonPrimitive(number);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a number", exception);
        }
    }

    private static JsonArray parseList(String input, JsonArray current) throws IOException {
        JsonObject parsed = TomlCodec.toJsonElement("value = " + input).getAsJsonObject();
        JsonElement value = parsed.get("value");
        if (parsed.size() != 1 || value == null || !value.isJsonArray() || kind(value) != Kind.LIST) {
            throw new IllegalArgumentException("Enter a list such as [\"stone\", \"deepslate\"]");
        }
        JsonArray result = value.getAsJsonArray();
        validateList(current, result);
        return result;
    }

    private static void validateReplacement(JsonElement expected, JsonElement replacement) {
        Kind expectedKind = kind(expected);
        Kind replacementKind = kind(replacement);
        if (expectedKind == Kind.TABLE || expectedKind == Kind.TABLE_ARRAY) {
            throw new IllegalArgumentException("Open the section to edit its settings");
        }
        boolean numericReplacement = (expectedKind == Kind.INTEGER || expectedKind == Kind.DECIMAL)
                && (replacementKind == Kind.INTEGER || replacementKind == Kind.DECIMAL);
        if (replacementKind != expectedKind && !numericReplacement) {
            throw new IllegalArgumentException("The replacement must have the same value type");
        }
        if (expectedKind == Kind.LIST) {
            validateList(expected.getAsJsonArray(), replacement.getAsJsonArray());
        }
        if (replacementKind == Kind.INTEGER) {
            parseInteger(replacement.getAsString());
        } else if (replacementKind == Kind.DECIMAL) {
            parseDecimal(replacement.getAsString());
        }
    }

    private static void validateList(JsonArray expected, JsonArray replacement) {
        for (JsonElement value : replacement) {
            Kind valueKind = kind(value);
            if (valueKind == Kind.INTEGER) {
                parseInteger(value.getAsString());
            } else if (valueKind == Kind.DECIMAL) {
                parseDecimal(value.getAsString());
            }
        }
        if (expected.size() == 0) {
            return;
        }
        Kind expectedKind = kind(expected.get(0));
        for (JsonElement value : expected) {
            if (kind(value) != expectedKind) {
                return;
            }
        }
        for (JsonElement value : replacement) {
            if (kind(value) != expectedKind) {
                throw new IllegalArgumentException("List entries must keep their current value type");
            }
        }
    }

    public enum Kind {
        TABLE, TABLE_ARRAY, BOOLEAN, INTEGER, DECIMAL, TEXT, LIST
    }

    public record Entry(List<String> path, String name, Kind kind, JsonElement value) {
        public Entry {
            path = List.copyOf(path);
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            value = Objects.requireNonNull(value, "value").deepCopy();
        }

        @Override
        public JsonElement value() {
            return value.deepCopy();
        }
    }

    public record Edit(ConfigEditorDocument original, List<String> path, JsonElement value) {
        public Edit {
            Objects.requireNonNull(original, "original");
            path = List.copyOf(path);
            value = Objects.requireNonNull(value, "value").deepCopy();
            validateReplacement(original.resolve(path), value);
        }

        public JsonElement expected() {
            return original.value(path);
        }

        @Override
        public JsonElement value() {
            return value.deepCopy();
        }
    }
}
