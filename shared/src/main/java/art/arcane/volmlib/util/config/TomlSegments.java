package art.arcane.volmlib.util.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits a TOML document at its top-level table headers. toml4j copies the rest of the document for every value it
 * reads, so one large document costs quadratic time while its tables parsed one by one cost linear time.
 */
final class TomlSegments {
    private TomlSegments() {
    }

    /**
     * The document's pieces, each starting at a table header except the first. Returns the whole document as one
     * piece when it holds an array of tables or repeats a header, so toml4j sees those exactly as written.
     */
    static List<String> split(String source) {
        List<String> segments = new ArrayList<>();
        Set<String> headers = new HashSet<>();
        int length = source.length();
        int start = 0;
        int depth = 0;
        boolean lineStart = true;
        int index = 0;
        while (index < length) {
            char current = source.charAt(index);
            if (current == '\n') {
                lineStart = true;
                index++;
                continue;
            }
            if (lineStart && (current == ' ' || current == '\t' || current == '\r')) {
                index++;
                continue;
            }
            if (lineStart && depth == 0 && current == '[') {
                if (index + 1 < length && source.charAt(index + 1) == '[') {
                    return List.of(source);
                }
                int lineEnd = source.indexOf('\n', index);
                int end = lineEnd < 0 ? length : lineEnd;
                String line = source.substring(index, end);
                if (!headers.add(line.substring(0, line.lastIndexOf(']') + 1).trim())) {
                    return List.of(source);
                }
                int lineBegin = source.lastIndexOf('\n', index) + 1;
                if (lineBegin > start) {
                    segments.add(source.substring(start, lineBegin));
                }
                start = lineBegin;
                lineStart = false;
                index = end;
                continue;
            }
            lineStart = false;
            if (current == '#') {
                int lineEnd = source.indexOf('\n', index);
                index = lineEnd < 0 ? length : lineEnd;
            } else if (current == '"' || current == '\'') {
                index = skipString(source, index, current);
            } else {
                if (current == '[') {
                    depth++;
                } else if (current == ']' && depth > 0) {
                    depth--;
                }
                index++;
            }
        }
        if (start < length || segments.isEmpty()) {
            segments.add(source.substring(start));
        }
        return segments;
    }

    private static int skipString(String source, int index, char quote) {
        boolean basic = quote == '"';
        boolean multiline = source.startsWith(String.valueOf(quote).repeat(3), index);
        int position = index + (multiline ? 3 : 1);
        int length = source.length();
        while (position < length) {
            char current = source.charAt(position);
            if (basic && current == '\\') {
                position += 2;
                continue;
            }
            if (!multiline && current == '\n') {
                return position;
            }
            if (current == quote && (!multiline || source.startsWith(String.valueOf(quote).repeat(3), position))) {
                return position + (multiline ? 3 : 1);
            }
            position++;
        }
        return length;
    }
}
