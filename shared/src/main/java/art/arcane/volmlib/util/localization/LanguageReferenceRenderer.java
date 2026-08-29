package art.arcane.volmlib.util.localization;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class LanguageReferenceRenderer {
    private static final Pattern BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private LanguageReferenceRenderer() {
    }

    public static String render(MessageCatalog catalog, List<String> headerLines) {
        MessageCatalog requiredCatalog = Objects.requireNonNull(catalog, "Message catalog cannot be null");
        List<String> requiredHeader = List.copyOf(Objects.requireNonNull(headerLines, "Header lines cannot be null"));
        Section root = new Section();
        for (MessageKey key : requiredCatalog.keys()) {
            root.put(key.id(), key.englishValue());
        }

        StringBuilder output = new StringBuilder();
        for (String line : requiredHeader) {
            output.append("# ").append(line).append('\n');
        }
        root.render(output, "");
        return output.toString();
    }

    private static String formatKey(String key) {
        return BARE_KEY.matcher(key).matches() ? key : '"' + escape(key) + '"';
    }

    private static String formatPath(String path) {
        String[] segments = path.split("\\.", -1);
        StringBuilder output = new StringBuilder(path.length() + 4);
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                output.append('.');
            }
            output.append(formatKey(segments[index]));
        }
        return output.toString();
    }

    private static String formatText(String value) {
        return '"' + escape(value) + '"';
    }

    private static String formatLines(List<String> lines) {
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append(formatText(lines.get(index)));
        }
        return output.append(']').toString();
    }

    private static String escape(String value) {
        StringBuilder output = new StringBuilder(value.length() + 8);
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
        return output.toString();
    }

    private static String join(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    private static final class Section {
        private final Map<String, MessageValue> values = new LinkedHashMap<>();
        private final Map<String, Section> children = new LinkedHashMap<>();

        private void put(String id, MessageValue value) {
            Section section = this;
            int cursor = 0;
            while (true) {
                int dot = id.indexOf('.', cursor);
                if (dot < 0) {
                    break;
                }
                String segment = id.substring(cursor, dot);
                if (section.values.containsKey(segment)) {
                    throw new IllegalStateException("Message id collides with a message key: " + id);
                }
                section = section.children.computeIfAbsent(segment, ignored -> new Section());
                cursor = dot + 1;
            }

            String leaf = id.substring(cursor);
            if (section.children.containsKey(leaf)) {
                throw new IllegalStateException("Message id collides with a message group: " + id);
            }
            section.values.put(leaf, value);
        }

        private boolean hasInlineValues() {
            for (MessageValue value : values.values()) {
                if (!(value instanceof PluralValue)) {
                    return true;
                }
            }
            return false;
        }

        private void render(StringBuilder output, String path) {
            if (hasInlineValues()) {
                openTable(output, path);
                renderInlineValues(output);
            }

            for (Map.Entry<String, MessageValue> entry : values.entrySet()) {
                if (entry.getValue() instanceof PluralValue plural) {
                    openTable(output, join(path, entry.getKey()));
                    for (Map.Entry<String, String> form : plural.forms().entrySet()) {
                        output.append(formatKey(form.getKey())).append(" = ")
                                .append(formatText(form.getValue())).append('\n');
                    }
                }
            }

            for (Map.Entry<String, Section> child : children.entrySet()) {
                child.getValue().render(output, join(path, child.getKey()));
            }
        }

        private void renderInlineValues(StringBuilder output) {
            for (Map.Entry<String, MessageValue> entry : values.entrySet()) {
                MessageValue value = entry.getValue();
                if (value instanceof TextValue text) {
                    output.append(formatKey(entry.getKey())).append(" = ")
                            .append(formatText(text.template())).append('\n');
                } else if (value instanceof LinesValue lines) {
                    output.append(formatKey(entry.getKey())).append(" = ")
                            .append(formatLines(lines.lines())).append('\n');
                }
            }
        }

        private void openTable(StringBuilder output, String path) {
            if (!output.isEmpty()) {
                output.append('\n');
            }
            if (!path.isEmpty()) {
                output.append('[').append(formatPath(path)).append(']').append('\n');
            }
        }
    }
}
