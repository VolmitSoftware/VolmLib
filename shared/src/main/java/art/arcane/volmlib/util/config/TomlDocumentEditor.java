package art.arcane.volmlib.util.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TomlDocumentEditor {
    private TomlDocumentEditor() {
    }

    public static String set(String source, List<String> path, JsonElement value) throws IOException {
        Objects.requireNonNull(source, "source");
        List<String> requestedPath = List.copyOf(path);
        if (requestedPath.isEmpty()) {
            throw new IllegalArgumentException("TOML setting path is empty");
        }
        requireEditableValue(value);
        JsonObject original = parse(source);
        JsonObject expected = original.deepCopy();
        List<String> resolvedPath = replaceValue(expected, requestedPath, value);
        if (original.equals(expected)) {
            return source;
        }

        SourceScanner scanner = new SourceScanner(source);
        scanner.scan();
        Span span = scanner.values.get(resolvedPath);
        String rendered = TomlCodec.toInlineToml(value);
        String updated;
        if (span != null) {
            JsonElement previous = resolve(original, resolvedPath);
            updated = previous.isJsonArray() && value.isJsonArray()
                    ? replaceArray(source, scanner, resolvedPath, previous.getAsJsonArray(), value.getAsJsonArray())
                    : source.substring(0, span.start()) + rendered + source.substring(span.end());
        } else if (requestedPath.size() == 1 && !original.has(requestedPath.get(0))) {
            String newline = source.contains("\r\n") ? "\r\n" : "\n";
            int insertion = scanner.firstStatement;
            if (insertion < source.length()) {
                while (insertion > 0 && (source.charAt(insertion - 1) == ' ' || source.charAt(insertion - 1) == '\t')) {
                    insertion--;
                }
            }
            String prefix = source.substring(0, insertion);
            if (!prefix.isEmpty() && !prefix.endsWith("\n") && !prefix.endsWith("\r")) {
                prefix += newline;
            }
            updated = prefix + TomlCodec.toInlineToml(new JsonPrimitive(requestedPath.get(0)))
                    + " = " + rendered + newline + source.substring(insertion);
        } else {
            throw new IOException("TOML setting has no editable value: " + requestedPath);
        }
        if (!parse(updated).equals(expected)) {
            throw new IOException("TOML edit would change unrelated settings: " + requestedPath);
        }
        return updated;
    }

    private static String replaceArray(String source, SourceScanner scanner, List<String> path,
                                       JsonArray previous, JsonArray replacement) {
        List<TextEdit> edits = new ArrayList<>();
        List<Integer> commas = scanner.arrayCommas.get(path);
        for (int index = 0; index < previous.size(); index++) {
            List<String> child = new ArrayList<>(path);
            child.add(Integer.toString(index));
            Span span = scanner.values.get(child);
            if (index >= replacement.size()) {
                edits.add(new TextEdit(span.start(), span.end(), ""));
                if (index < commas.size()) {
                    int comma = commas.get(index);
                    edits.add(new TextEdit(comma, comma + 1, ""));
                }
            } else if (!previous.get(index).equals(replacement.get(index))) {
                edits.add(new TextEdit(span.start(), span.end(), TomlCodec.toInlineToml(replacement.get(index))));
            }
        }
        if (replacement.size() > previous.size()) {
            int missingComma = -1;
            if (previous.size() > 0 && commas.size() < previous.size()) {
                List<String> lastPath = new ArrayList<>(path);
                lastPath.add(Integer.toString(previous.size() - 1));
                missingComma = scanner.values.get(lastPath).end();
            }
            Span array = scanner.values.get(path);
            int closing = array.end() - 1;
            String newline = source.contains("\r\n") ? "\r\n" : "\n";
            boolean multiline = source.substring(array.start(), closing).contains("\n");
            List<String> additions = new ArrayList<>();
            for (int index = previous.size(); index < replacement.size(); index++) {
                additions.add(TomlCodec.toInlineToml(replacement.get(index)));
            }
            String inserted;
            if (multiline) {
                int lineStart = source.lastIndexOf('\n', closing - 1) + 1;
                String closingLine = source.substring(lineStart, closing);
                String indentation = closingLine.isBlank() ? closingLine : "";
                String prefix = closingLine.isBlank() ? "    " : newline + "    ";
                inserted = prefix + String.join("," + newline + indentation + "    ", additions)
                        + newline + indentation;
            } else {
                boolean needsSpace = previous.size() > 0 && !Character.isWhitespace(source.charAt(closing - 1));
                inserted = (needsSpace ? " " : "") + String.join(", ", additions);
            }
            if (missingComma == closing) {
                inserted = "," + inserted;
            } else if (missingComma >= 0) {
                edits.add(new TextEdit(missingComma, missingComma, ","));
            }
            edits.add(new TextEdit(closing, closing, inserted));
        }
        edits.sort(Comparator.comparingInt(TextEdit::start).reversed());
        StringBuilder updated = new StringBuilder(source);
        for (TextEdit edit : edits) {
            updated.replace(edit.start(), edit.end(), edit.replacement());
        }
        return updated.toString();
    }

    private static JsonElement resolve(JsonObject root, List<String> path) {
        JsonElement current = root;
        for (String segment : path) {
            current = current.isJsonObject() ? current.getAsJsonObject().get(segment)
                    : current.getAsJsonArray().get(Integer.parseInt(segment));
        }
        return current;
    }

    private static JsonObject parse(String source) throws IOException {
        JsonElement parsed = TomlCodec.toJsonElement(source);
        if (parsed == null || !parsed.isJsonObject()) {
            throw new IOException("Configuration is not a TOML table");
        }
        return parsed.getAsJsonObject();
    }

    private static void requireEditableValue(JsonElement value) {
        Objects.requireNonNull(value, "value");
        if (value.isJsonPrimitive()) {
            return;
        }
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) {
                if (!entry.isJsonPrimitive()) {
                    throw new IllegalArgumentException("TOML setting must be a scalar or an array of scalars");
                }
            }
            return;
        }
        throw new IllegalArgumentException("TOML setting must be a scalar or an array of scalars");
    }

    private static List<String> replaceValue(JsonObject root, List<String> path, JsonElement value) throws IOException {
        JsonElement current = root;
        List<String> resolved = new ArrayList<>(path.size());
        for (int position = 0; position < path.size(); position++) {
            String segment = path.get(position);
            boolean last = position == path.size() - 1;
            if (current.isJsonObject()) {
                JsonObject table = current.getAsJsonObject();
                resolved.add(segment);
                if (!table.has(segment) && !(last && position == 0)) {
                    throw new IOException("TOML setting does not exist: " + path);
                }
                if (last) {
                    table.add(segment, value.deepCopy());
                } else {
                    current = table.get(segment);
                }
            } else if (current.isJsonArray()) {
                JsonArray array = current.getAsJsonArray();
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid TOML array index: " + segment, exception);
                }
                if (index < 0 || index >= array.size()) {
                    throw new IOException("TOML array index is out of range: " + segment);
                }
                resolved.add(Integer.toString(index));
                if (last) {
                    array.set(index, value.deepCopy());
                } else {
                    current = array.get(index);
                }
            } else {
                throw new IOException("TOML setting has no child values: " + path);
            }
        }
        return List.copyOf(resolved);
    }

    private record Span(int start, int end) {
    }

    private record TextEdit(int start, int end, String replacement) {
    }

    private static final class SourceScanner {
        private final String source;
        private final Map<List<String>, Span> values = new HashMap<>();
        private final Map<List<String>, List<Integer>> arrayCommas = new HashMap<>();
        private final Map<List<String>, Integer> arrayIndices = new HashMap<>();
        private List<String> table = List.of();
        private int position;
        private int firstStatement;

        private SourceScanner(String source) {
            this.source = source;
        }

        private void scan() throws IOException {
            skipTrivia();
            firstStatement = position;
            while (position < source.length()) {
                if (source.charAt(position) == '[') {
                    readHeader();
                } else {
                    readAssignment(table);
                }
                skipTrivia();
            }
        }

        private void readHeader() throws IOException {
            position++;
            boolean array = take('[');
            List<String> keys = readKey(']');
            require(']');
            if (array) {
                require(']');
            }
            List<String> resolved = new ArrayList<>();
            for (int index = 0; index < keys.size(); index++) {
                resolved.add(keys.get(index));
                List<String> prefix = List.copyOf(resolved);
                if (array && index == keys.size() - 1) {
                    int entry = arrayIndices.getOrDefault(prefix, -1) + 1;
                    arrayIndices.put(prefix, entry);
                    resolved.add(Integer.toString(entry));
                } else if (arrayIndices.containsKey(prefix)) {
                    resolved.add(Integer.toString(arrayIndices.get(prefix)));
                }
            }
            table = List.copyOf(resolved);
        }

        private void readAssignment(List<String> parent) throws IOException {
            List<String> path = new ArrayList<>(parent);
            path.addAll(readKey('='));
            require('=');
            skipTrivia();
            readValue(List.copyOf(path));
        }

        private List<String> readKey(char terminator) throws IOException {
            List<String> keys = new ArrayList<>();
            while (position < source.length()) {
                skipHorizontalWhitespace();
                if (position >= source.length()) {
                    throw invalidSource();
                }
                int start = position;
                char character = source.charAt(position);
                if (character == '\'' || character == '"') {
                    readString();
                    JsonObject decoded = parse("key = " + source.substring(start, position));
                    keys.add(decoded.get("key").getAsString());
                } else {
                    while (position < source.length() && isBareKey(source.charAt(position))) {
                        position++;
                    }
                    if (start == position) {
                        throw invalidSource();
                    }
                    keys.add(source.substring(start, position));
                }
                skipHorizontalWhitespace();
                if (position < source.length() && source.charAt(position) == terminator) {
                    return keys;
                }
                require('.');
            }
            throw invalidSource();
        }

        private void readValue(List<String> path) throws IOException {
            int start = position;
            if (position >= source.length()) {
                throw invalidSource();
            }
            char character = source.charAt(position);
            if (character == '\'' || character == '"') {
                readString();
            } else if (character == '[') {
                readArray(path);
            } else if (character == '{') {
                readInlineTable(path);
            } else {
                while (position < source.length() && !isValueBoundary(source.charAt(position))) {
                    position++;
                }
                while (position > start && isHorizontalWhitespace(source.charAt(position - 1))) {
                    position--;
                }
                if (position == start) {
                    throw invalidSource();
                }
            }
            values.put(path, new Span(start, position));
        }

        private void readArray(List<String> parent) throws IOException {
            position++;
            skipTrivia();
            int index = 0;
            List<Integer> commas = new ArrayList<>();
            arrayCommas.put(parent, commas);
            while (!take(']')) {
                List<String> path = new ArrayList<>(parent);
                path.add(Integer.toString(index++));
                readValue(List.copyOf(path));
                skipTrivia();
                if (!take(',')) {
                    require(']');
                    return;
                }
                commas.add(position - 1);
                skipTrivia();
            }
        }

        private void readInlineTable(List<String> parent) throws IOException {
            position++;
            skipTrivia();
            while (!take('}')) {
                readAssignment(parent);
                skipTrivia();
                if (!take(',')) {
                    require('}');
                    return;
                }
                skipTrivia();
            }
        }

        private void readString() throws IOException {
            char quote = source.charAt(position++);
            boolean multiline = position + 1 < source.length()
                    && source.charAt(position) == quote && source.charAt(position + 1) == quote;
            if (multiline) {
                position += 2;
            }
            while (position < source.length()) {
                char character = source.charAt(position++);
                if (character == '\\' && quote == '"') {
                    if (position >= source.length()) {
                        throw invalidSource();
                    }
                    position++;
                } else if (character == quote) {
                    if (!multiline) {
                        return;
                    }
                    int run = 1;
                    while (position < source.length() && source.charAt(position) == quote) {
                        run++;
                        position++;
                    }
                    if (run >= 3) {
                        return;
                    }
                }
            }
            throw invalidSource();
        }

        private void skipTrivia() {
            while (position < source.length()) {
                char character = source.charAt(position);
                if (character == '#') {
                    while (position < source.length() && source.charAt(position) != '\n'
                            && source.charAt(position) != '\r') {
                        position++;
                    }
                } else if (character == ' ' || character == '\t' || character == '\n' || character == '\r') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private void skipHorizontalWhitespace() {
            while (position < source.length() && isHorizontalWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private boolean take(char expected) {
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void require(char expected) throws IOException {
            if (!take(expected)) {
                throw invalidSource();
            }
        }

        private IOException invalidSource() {
            return new IOException("Unable to locate TOML setting at character " + position);
        }

        private static boolean isBareKey(char character) {
            return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9' || character == '_' || character == '-';
        }

        private static boolean isHorizontalWhitespace(char character) {
            return character == ' ' || character == '\t';
        }

        private static boolean isValueBoundary(char character) {
            return character == '\n' || character == '\r' || character == '#' || character == ','
                    || character == ']' || character == '}';
        }
    }
}
