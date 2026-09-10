package art.arcane.volmlib.util.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TomlCodec {
    private static final char SURROGATE_FIRST = '\uD800';
    private static final char SURROGATE_LAST = '\uDBFF';

    private TomlCodec() {
    }

    public static <T> T fromToml(String raw, Class<T> type) throws IOException {
        try {
            Object parsed = parseToml(raw);
            String json = ConfigJson.toJson(parsed, false);
            return ConfigJson.fromJson(json, type);
        } catch (Throwable e) {
            throw new IOException("Invalid toml", e);
        }
    }

    public static JsonElement toJsonElement(String raw) throws IOException {
        try {
            Object parsed = parseToml(raw);
            String json = ConfigJson.toJson(parsed, false);
            return ConfigJson.fromJson(json, JsonElement.class);
        } catch (Throwable e) {
            throw new IOException("Invalid toml", e);
        }
    }

    public static String toToml(Object object, String sourceTag) {
        return toToml(object, sourceTag, ConfigExposePolicy.ALL);
    }

    public static String toToml(Object object, String sourceTag, ConfigExposePolicy exposePolicy) {
        return new ReflectiveTomlWriter(sourceTag, exposePolicy).write(object);
    }

    public static String toToml(JsonElement element) {
        return new GenericTomlWriter().write(toGenericValue(element));
    }

    static String toInlineToml(JsonElement element) {
        Object value = toGenericValue(element);
        if (!isInlineValue(value)) {
            throw new IllegalArgumentException("TOML value must be a scalar or an array of scalars");
        }
        return formatInlineValue(value);
    }

    private static Object toGenericValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                values.put(entry.getKey(), toGenericValue(entry.getValue()));
            }
            return values;
        }
        if (element.isJsonArray()) {
            List<Object> values = new ArrayList<>(element.getAsJsonArray().size());
            for (JsonElement entry : element.getAsJsonArray()) {
                values.add(toGenericValue(entry));
            }
            return values;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        String number = primitive.getAsString();
        if (number.matches("[+-]?\\d+")) {
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException exception) {
                return new BigInteger(number);
            }
        }
        return new BigDecimal(number);
    }

    private static Object parseToml(String raw) {
        EscapedBackslashProtection protection = protectEscapedBackslashes(raw == null ? "" : raw);
        Toml toml = new Toml().read(protection.source());
        Map<String, Object> map = toml.toMap();
        if (map == null) {
            return new LinkedHashMap<String, Object>();
        }
        return normalizeKeys(map, protection.marker());
    }

    private static Object normalizeKeys(Object value, char escapedBackslashMarker) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                out.put(
                        normalizeKey(String.valueOf(entry.getKey()), escapedBackslashMarker),
                        normalizeKeys(entry.getValue(), escapedBackslashMarker)
                );
            }
            return out;
        }

        if (value instanceof Collection<?> collection) {
            List<Object> out = new ArrayList<>(collection.size());
            for (Object item : collection) {
                out.add(normalizeKeys(item, escapedBackslashMarker));
            }
            return out;
        }

        if (value instanceof String string) {
            return restoreEscapedBackslashes(string, escapedBackslashMarker);
        }

        return value;
    }

    private static String normalizeKey(String key, char escapedBackslashMarker) {
        if (key.length() < 2) {
            return restoreEscapedBackslashes(key, escapedBackslashMarker);
        }

        if (key.charAt(0) == '"' && key.charAt(key.length() - 1) == '"') {
            return restoreEscapedBackslashes(
                    unescape(key.substring(1, key.length() - 1)),
                    escapedBackslashMarker
            );
        }
        return restoreEscapedBackslashes(key, escapedBackslashMarker);
    }

    private static EscapedBackslashProtection protectEscapedBackslashes(String source) {
        if (!source.contains("\\\\")) {
            return new EscapedBackslashProtection(source, '\0');
        }
        char marker = unusedSurrogate(source);
        StringBuilder protectedSource = new StringBuilder(source.length());
        TomlStringState state = TomlStringState.PLAIN;
        boolean changed = false;
        for (int index = 0; index < source.length(); ) {
            char current = source.charAt(index);
            if (state == TomlStringState.COMMENT) {
                protectedSource.append(current);
                index++;
                if (current == '\n' || current == '\r') {
                    state = TomlStringState.PLAIN;
                }
                continue;
            }
            if (state == TomlStringState.LITERAL) {
                protectedSource.append(current);
                index++;
                if (current == '\'') {
                    state = TomlStringState.PLAIN;
                }
                continue;
            }
            if (state == TomlStringState.MULTILINE_LITERAL) {
                int quotes = quoteRun(source, index, '\'');
                if (quotes >= 3) {
                    protectedSource.append(source, index, index + quotes);
                    index += quotes;
                    state = TomlStringState.PLAIN;
                } else {
                    protectedSource.append(current);
                    index++;
                }
                continue;
            }
            if (state == TomlStringState.BASIC || state == TomlStringState.MULTILINE_BASIC) {
                if (current == '\\' && index + 1 < source.length()) {
                    char next = source.charAt(index + 1);
                    if (next == '\\') {
                        protectedSource.append(marker);
                        changed = true;
                    } else {
                        protectedSource.append(current).append(next);
                    }
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    int quotes = quoteRun(source, index, '"');
                    protectedSource.append(source, index, index + quotes);
                    index += quotes;
                    if (state == TomlStringState.BASIC || quotes >= 3) {
                        state = TomlStringState.PLAIN;
                    }
                    continue;
                }
                protectedSource.append(current);
                index++;
                continue;
            }
            if (current == '#') {
                protectedSource.append(current);
                index++;
                state = TomlStringState.COMMENT;
                continue;
            }
            if (startsWith(source, index, "\"\"\"")) {
                protectedSource.append("\"\"\"");
                index += 3;
                state = TomlStringState.MULTILINE_BASIC;
                continue;
            }
            if (startsWith(source, index, "'''")) {
                protectedSource.append("'''");
                index += 3;
                state = TomlStringState.MULTILINE_LITERAL;
                continue;
            }
            protectedSource.append(current);
            index++;
            if (current == '"') {
                state = TomlStringState.BASIC;
            } else if (current == '\'') {
                state = TomlStringState.LITERAL;
            }
        }
        if (!changed) {
            return new EscapedBackslashProtection(source, '\0');
        }
        return new EscapedBackslashProtection(protectedSource.toString(), marker);
    }

    private static char unusedSurrogate(String source) {
        for (char marker = SURROGATE_FIRST; marker <= SURROGATE_LAST; marker++) {
            if (source.indexOf(marker) < 0) {
                return marker;
            }
        }
        throw new IllegalArgumentException("TOML source exhausts the surrogate markers required for safe parsing");
    }

    private static int quoteRun(String source, int index, char quote) {
        int end = index;
        while (end < source.length() && source.charAt(end) == quote) {
            end++;
        }
        return end - index;
    }

    private static boolean startsWith(String source, int index, String expected) {
        return index + expected.length() <= source.length() && source.startsWith(expected, index);
    }

    private static String restoreEscapedBackslashes(String value, char marker) {
        if (marker == '\0' || value.indexOf(marker) < 0) {
            return value;
        }
        return value.replace(marker, '\\');
    }

    private static String unescape(String input) {
        if (input.indexOf('\\') < 0) {
            return input;
        }

        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c != '\\' || i + 1 >= input.length()) {
                out.append(c);
                continue;
            }

            char next = input.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static List<Field> getSerializableFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        collectFields(type, out);
        return out;
    }

    private static void collectFields(Class<?> type, List<Field> out) {
        if (type == null || type == Object.class) {
            return;
        }

        collectFields(type.getSuperclass(), out);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                continue;
            }
            field.setAccessible(true);
            out.add(field);
        }
    }

    private static Object getFieldValue(Field field, Object object) {
        try {
            return field.get(object);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isInlineValue(Object value) {
        if (value == null) {
            return true;
        }

        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>) {
            return true;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object v = Array.get(value, i);
                if (!isInlineValue(v) || v instanceof Map<?, ?> || v instanceof Collection<?>) {
                    return false;
                }
            }
            return true;
        }

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (!isInlineValue(item) || item instanceof Map<?, ?> || item instanceof Collection<?>) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    private static String formatInlineValue(Object value) {
        if (value == null) {
            return "\"\"";
        }

        if (value instanceof String string) {
            return '"' + escape(string) + '"';
        }
        if (value instanceof Character c) {
            return '"' + escape(String.valueOf(c)) + '"';
        }
        if (value instanceof Enum<?> enumValue) {
            return '"' + escape(enumValue.name()) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<String> parts = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                parts.add(formatInlineValue(Array.get(value, i)));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>(collection.size());
            for (Object item : collection) {
                parts.add(formatInlineValue(item));
            }
            return "[" + String.join(", ", parts) + "]";
        }

        return '"' + escape(String.valueOf(value)) + '"';
    }

    private static String renderPath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String[] parts = path.split("\\.");
        List<String> rendered = new ArrayList<>(parts.length);
        for (String part : parts) {
            rendered.add(formatKey(part));
        }
        return String.join(".", rendered);
    }

    private static String formatKey(String key) {
        if (key == null || key.isBlank()) {
            return "\"\"";
        }

        if (key.matches("[A-Za-z0-9_-]+")) {
            return key;
        }
        return '"' + escape(key) + '"';
    }

    private static String joinPath(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        if (b == null || b.isBlank()) {
            return a;
        }
        return a + "." + b;
    }

    private static String escape(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String normalized = raw.replace("\r\n", "\n").stripTrailing();
        if (!normalized.isEmpty()) {
            normalized += "\n";
        }
        return normalized;
    }

    private record EscapedBackslashProtection(String source, char marker) {
    }

    private enum TomlStringState {
        PLAIN,
        COMMENT,
        BASIC,
        MULTILINE_BASIC,
        LITERAL,
        MULTILINE_LITERAL
    }

    private static final class ReflectiveTomlWriter {
        private final StringBuilder out = new StringBuilder();
        private final String sourceTag;
        private final ConfigExposePolicy exposePolicy;

        private ReflectiveTomlWriter(String sourceTag, ConfigExposePolicy exposePolicy) {
            this.sourceTag = sourceTag == null ? "config" : sourceTag;
            this.exposePolicy = exposePolicy == null ? ConfigExposePolicy.ALL : exposePolicy;
        }

        private String write(Object root) {
            if (root == null) {
                return "";
            }

            out.append("# Configuration - ").append(sourceTag).append('\n');
            out.append("# This file is canonicalized on load; comments and new keys may update automatically.\n");
            ConfigDescription desc = root.getClass().getAnnotation(ConfigDescription.class);
            if (desc != null && !desc.value().isBlank()) {
                out.append("#\n");
                out.append("# ").append(desc.value().strip()).append('\n');
            }
            out.append('\n');
            writePojoSection("", root);
            return normalize(out.toString());
        }

        private void writePojoSection(String path, Object sectionObject) {
            if (sectionObject == null) {
                return;
            }

            List<Field> fields = getSerializableFields(sectionObject.getClass());
            List<Field> deferred = new ArrayList<>();

            for (Field field : fields) {
                Object value = getFieldValue(field, sectionObject);
                if (value == null) {
                    continue;
                }
                if (!exposePolicy.expose(sourceTag, path, field, value)) {
                    continue;
                }

                if (isInlineValue(value)) {
                    writeFieldComments(path, field, value);
                    out.append(formatKey(field.getName())).append(" = ").append(formatInlineValue(value)).append('\n');
                } else {
                    deferred.add(field);
                }
            }

            for (Field field : deferred) {
                Object value = getFieldValue(field, sectionObject);
                if (value == null) {
                    continue;
                }
                if (!exposePolicy.expose(sourceTag, path, field, value)) {
                    continue;
                }

                String childPath = joinPath(path, field.getName());
                if (value instanceof Collection<?> || value.getClass().isArray()) {
                    writeArraySections(childPath, value);
                    continue;
                }
                if (value instanceof Map<?, ?> map) {
                    writeMapSection(childPath, map, field);
                    continue;
                }

                writeSectionHeader(childPath,
                        ConfigDocumentation.buildSectionComments(sourceTag, childPath, field, value));
                writePojoSection(childPath, value);
            }
        }

        private void writeMapSection(String sectionPath, Map<?, ?> map, Field sourceField) {
            writeSectionHeader(sectionPath, ConfigDocumentation.buildFieldComments(sourceTag, sectionPath, sourceField, map));
            if (map.isEmpty()) {
                return;
            }

            List<Map.Entry<?, ?>> deferred = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry == null || entry.getKey() == null) {
                    continue;
                }

                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }

                if (isInlineValue(value)) {
                    out.append(formatKey(String.valueOf(entry.getKey())))
                            .append(" = ")
                            .append(formatInlineValue(value))
                            .append('\n');
                } else {
                    deferred.add(entry);
                }
            }

            for (Map.Entry<?, ?> entry : deferred) {
                Object value = entry.getValue();
                if (value == null || entry.getKey() == null) {
                    continue;
                }

                String childPath = joinPath(sectionPath, String.valueOf(entry.getKey()));
                if (value instanceof Collection<?> || value.getClass().isArray()) {
                    writeArraySections(childPath, value);
                } else if (value instanceof Map<?, ?> nested) {
                    writeSectionHeader(childPath, List.of());
                    writeMapBody(childPath, nested);
                } else {
                    writeSectionHeader(childPath, List.of());
                    writePojoSection(childPath, value);
                }
            }
        }

        private void writeMapBody(String sectionPath, Map<?, ?> map) {
            List<Map.Entry<?, ?>> deferred = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                if (isInlineValue(entry.getValue())) {
                    out.append(formatKey(String.valueOf(entry.getKey())))
                            .append(" = ")
                            .append(formatInlineValue(entry.getValue()))
                            .append('\n');
                } else {
                    deferred.add(entry);
                }
            }

            for (Map.Entry<?, ?> entry : deferred) {
                String childPath = joinPath(sectionPath, String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof Collection<?> || value.getClass().isArray()) {
                    writeArraySections(childPath, value);
                } else if (value instanceof Map<?, ?> nested) {
                    writeSectionHeader(childPath, List.of());
                    writeMapBody(childPath, nested);
                } else {
                    writeSectionHeader(childPath, List.of());
                    writePojoSection(childPath, value);
                }
            }
        }

        private void writeFieldComments(String path, Field field, Object value) {
            List<String> comments = ConfigDocumentation.buildFieldComments(sourceTag, path, field, value);
            for (String comment : comments) {
                if (comment == null || comment.isBlank()) {
                    continue;
                }
                out.append("# ").append(comment.strip()).append('\n');
            }
        }

        private void writeArraySections(String path, Object values) {
            if (values instanceof Collection<?> collection) {
                for (Object entry : collection) {
                    writeArrayEntry(path, entry);
                }
                return;
            }
            for (int index = 0; index < Array.getLength(values); index++) {
                writeArrayEntry(path, Array.get(values, index));
            }
        }

        private void writeArrayEntry(String path, Object value) {
            if (value == null || isInlineValue(value) || value instanceof Collection<?> || value.getClass().isArray()) {
                throw new IllegalArgumentException("TOML table arrays must contain only objects: " + path);
            }
            out.append('\n').append("[[").append(renderPath(path)).append("]]\n");
            if (value instanceof Map<?, ?> map) {
                writeMapBody(path, map);
            } else {
                writePojoSection(path, value);
            }
        }

        private void writeSectionHeader(String path, List<String> comments) {
            if (path == null || path.isBlank()) {
                return;
            }

            if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }

            for (String comment : comments) {
                if (comment == null || comment.isBlank()) {
                    continue;
                }
                out.append("# ").append(comment.strip()).append('\n');
            }
            out.append('[').append(renderPath(path)).append(']').append('\n');
        }
    }

    private static final class GenericTomlWriter {
        private final StringBuilder out = new StringBuilder();

        private String write(Object root) {
            if (root instanceof Map<?, ?> map) {
                writeMapSection(List.of(), map);
                return normalize(out.toString());
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("value", root);
            writeMapSection(List.of(), wrapped);
            return normalize(out.toString());
        }

        private void writeMapSection(List<String> path, Map<?, ?> map) {
            boolean hasInlineValues = hasInlineValues(map);
            if (!path.isEmpty() && (hasInlineValues || map.isEmpty())) {
                writeSectionHeader(path);
            }

            writeMapBody(path, map);
        }

        private void writeMapBody(List<String> path, Map<?, ?> map) {
            List<Map.Entry<?, ?>> deferred = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }

                Object value = entry.getValue();
                if (isInlineValue(value)) {
                    out.append(formatKey(String.valueOf(entry.getKey())))
                            .append(" = ")
                            .append(formatInlineValue(value))
                            .append('\n');
                } else {
                    deferred.add(entry);
                }
            }

            for (Map.Entry<?, ?> entry : deferred) {
                List<String> childPath = new ArrayList<>(path.size() + 1);
                childPath.addAll(path);
                childPath.add(String.valueOf(entry.getKey()));
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    writeMapSection(childPath, nested);
                } else if (value instanceof Collection<?> collection) {
                    writeArraySections(childPath, collection);
                } else {
                    throw new IllegalArgumentException("Unsupported TOML table value: " + childPath);
                }
            }
        }

        private void writeArraySections(List<String> path, Collection<?> values) {
            for (Object value : values) {
                if (!(value instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("TOML table arrays must contain only objects: " + path);
                }
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append('[');
                appendSectionPath(path);
                out.append("]\n");
                writeMapBody(path, map);
            }
        }

        private boolean hasInlineValues(Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry != null && entry.getKey() != null && entry.getValue() != null
                        && isInlineValue(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }

        private void writeSectionHeader(List<String> path) {
            if (!out.isEmpty()) {
                out.append('\n');
            }

            appendSectionPath(path);
            out.append('\n');
        }

        private void appendSectionPath(List<String> path) {
            out.append('[');
            for (int index = 0; index < path.size(); index++) {
                if (index > 0) {
                    out.append('.');
                }
                out.append(formatKey(path.get(index)));
            }
            out.append(']');
        }
    }
}
