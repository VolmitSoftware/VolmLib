package art.arcane.volmlib.util.localization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class TomlLanguageWriter {
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private TomlLanguageWriter() {
    }

    public static String render(Map<String, ? extends MessageValue> values, List<String> headerLines) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, ? extends MessageValue> entry : values.entrySet()) {
            root.add(entry.getKey(), serializeValue(entry.getValue()));
        }
        return renderJson(root, headerLines);
    }

    public static String renderText(Map<String, String> values, List<String> headerLines) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }
        return renderJson(root, headerLines);
    }

    public static String renderJson(JsonObject values, List<String> headerLines) {
        Node root = new Node();
        for (Map.Entry<String, JsonElement> entry : flattenedValues(values).entrySet()) {
            root.put(entry.getKey(), entry.getValue());
        }
        StringBuilder output = new StringBuilder();
        for (String line : List.copyOf(headerLines)) {
            output.append('#');
            if (!line.isEmpty()) {
                output.append(' ').append(line);
            }
            output.append('\n');
        }
        root.render(output, "");
        return output.toString();
    }

    static LinkedHashMap<String, JsonElement> flattenedValues(JsonObject values) {
        LinkedHashMap<String, JsonElement> flattened = new LinkedHashMap<>();
        flatten(flattened, Objects.requireNonNull(values, "values"), "");
        return flattened;
    }

    static JsonElement serializeValue(MessageValue value) {
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
        JsonObject forms = new JsonObject();
        for (Map.Entry<String, String> form : plural.forms().entrySet()) {
            forms.addProperty(form.getKey(), form.getValue());
        }
        return forms;
    }

    private static void flatten(Map<String, JsonElement> target, JsonObject source, String prefix) {
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String key = join(prefix, entry.getKey());
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && value.getAsJsonObject().size() > 0) {
                flatten(target, value.getAsJsonObject(), key);
            } else if (target.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate language value: " + key);
            }
        }
    }

    private static String formatKey(String key) {
        return BARE_KEY.matcher(key).matches() ? key : quote(key);
    }

    private static String formatPath(String path) {
        StringBuilder output = new StringBuilder();
        for (String segment : path.split("\\.", -1)) {
            if (!output.isEmpty()) {
                output.append('.');
            }
            output.append(formatKey(segment));
        }
        return output.toString();
    }

    private static String formatValue(JsonElement value) {
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            return primitive.isString() ? quote(primitive.getAsString()) : primitive.toString();
        }
        if (value.isJsonArray()) {
            StringBuilder output = new StringBuilder("[");
            for (JsonElement element : value.getAsJsonArray()) {
                if (output.length() > 1) {
                    output.append(", ");
                }
                output.append(formatValue(element));
            }
            return output.append(']').toString();
        }
        if (value.isJsonObject()) {
            StringBuilder output = new StringBuilder("{");
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                if (output.length() > 1) {
                    output.append(", ");
                }
                output.append(formatKey(entry.getKey())).append(" = ").append(formatValue(entry.getValue()));
            }
            return output.append('}').toString();
        }
        throw new IllegalArgumentException("TOML language values cannot be null");
    }

    private static String quote(String value) {
        StringBuilder output = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> output.append("\\\\");
                case '"' -> output.append("\\\"");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20 || character == 0x7F) {
                        output.append(String.format("\\u%04X", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        return output.append('"').toString();
    }

    private static String join(String prefix, String key) {
        return prefix.isEmpty() ? key : prefix + "." + key;
    }

    private static final class Node {
        private final Map<String, Node> children = new LinkedHashMap<>();
        private JsonElement value;

        private void put(String id, JsonElement entry) {
            Node node = this;
            for (String segment : id.split("\\.", -1)) {
                node = node.children.computeIfAbsent(segment, ignored -> new Node());
            }
            node.value = entry;
        }

        private void render(StringBuilder output, String path) {
            LinkedHashMap<String, JsonElement> inline = new LinkedHashMap<>();
            for (Map.Entry<String, Node> child : children.entrySet()) {
                if (child.getValue().value != null) {
                    child.getValue().collect(inline, child.getKey());
                }
            }
            if (!inline.isEmpty()) {
                if (!output.isEmpty()) {
                    output.append('\n');
                }
                if (!path.isEmpty()) {
                    output.append('[').append(formatPath(path)).append("]\n");
                }
                for (Map.Entry<String, JsonElement> entry : inline.entrySet()) {
                    output.append(formatKey(entry.getKey())).append(" = ")
                            .append(formatValue(entry.getValue())).append('\n');
                }
            }
            for (Map.Entry<String, Node> child : children.entrySet()) {
                if (child.getValue().value == null) {
                    child.getValue().render(output, join(path, child.getKey()));
                }
            }
        }

        private void collect(Map<String, JsonElement> entries, String prefix) {
            if (value != null) {
                entries.put(prefix, value);
            }
            for (Map.Entry<String, Node> child : children.entrySet()) {
                child.getValue().collect(entries, join(prefix, child.getKey()));
            }
        }
    }
}
