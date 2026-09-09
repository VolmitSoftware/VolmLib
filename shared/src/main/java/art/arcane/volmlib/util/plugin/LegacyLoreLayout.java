package art.arcane.volmlib.util.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LegacyLoreLayout {
    private LegacyLoreLayout() {
    }

    public static List<String> wrap(String legacy, int columns) {
        Objects.requireNonNull(legacy, "legacy");
        if (columns < 2) {
            throw new IllegalArgumentException("Lore width must be at least two columns");
        }
        Layout layout = new Layout(columns);
        for (String paragraph : legacy.split("\\R", -1)) {
            for (String word : paragraph.split("[ \\t]+")) {
                if (!word.isEmpty()) {
                    layout.word(word);
                }
            }
            layout.flush();
        }
        return List.copyOf(layout.lines);
    }

    private static int formatLength(String text, int index) {
        if (text.charAt(index) != '§' || index + 1 >= text.length()) {
            return 0;
        }
        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x' && index + 14 <= text.length()) {
            for (int digit = index + 2; digit < index + 14; digit += 2) {
                if (text.charAt(digit) != '§' || Character.digit(text.charAt(digit + 1), 16) < 0) {
                    return 0;
                }
            }
            return 14;
        }
        return "0123456789abcdefklmnor".indexOf(code) >= 0 ? 2 : 0;
    }

    private static int columns(int codePoint) {
        int kind = Character.getType(codePoint);
        if (kind == Character.NON_SPACING_MARK || kind == Character.COMBINING_SPACING_MARK
                || kind == Character.ENCLOSING_MARK || kind == Character.FORMAT) {
            return 0;
        }
        return switch (Character.UnicodeScript.of(codePoint)) {
            case HAN, HANGUL, HIRAGANA, KATAKANA -> 2;
            default -> codePoint >= 0x1F000 ? 2 : 1;
        };
    }

    private static int width(String text) {
        int width = 0;
        for (int index = 0; index < text.length();) {
            int formatting = formatLength(text, index);
            if (formatting > 0) {
                index += formatting;
            } else {
                int codePoint = text.codePointAt(index);
                width += columns(codePoint);
                index += Character.charCount(codePoint);
            }
        }
        return width;
    }

    private static final class Layout {
        private final int maximum;
        private final List<String> lines = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private String color = "";
        private String decorations = "";
        private int width;

        private Layout(int maximum) {
            this.maximum = maximum;
        }

        private void word(String word) {
            int wordWidth = width(word);
            if (width > 0 && wordWidth > 0) {
                if (width + 1 + wordWidth > maximum) {
                    flush();
                } else {
                    line.append(' ');
                    width++;
                }
            }
            for (int index = 0; index < word.length();) {
                int formatting = formatLength(word, index);
                if (formatting > 0) {
                    String code = word.substring(index, index + formatting);
                    line.append(code);
                    style(code);
                    index += formatting;
                    continue;
                }
                int codePoint = word.codePointAt(index);
                int occupied = columns(codePoint);
                if (width + occupied > maximum) {
                    flush();
                }
                line.appendCodePoint(codePoint);
                width += occupied;
                index += Character.charCount(codePoint);
            }
        }

        private void style(String code) {
            String normalized = code.toLowerCase(Locale.ROOT);
            char value = normalized.charAt(1);
            if (value == 'r') {
                color = "";
                decorations = "";
            } else if (value == 'x' || "0123456789abcdef".indexOf(value) >= 0) {
                color = normalized;
                decorations = "";
            } else if (!decorations.contains(normalized)) {
                decorations += normalized;
            }
        }

        private void flush() {
            lines.add(line.toString());
            line.setLength(0);
            line.append(color).append(decorations);
            width = 0;
        }
    }
}
