package art.arcane.volmlib.util.board;

import org.bukkit.ChatColor;

public class BoardEntry {
    static final int MAX_LINE_LENGTH = 32;
    private static final int MAX_PART_LENGTH = 16;

    private final String prefix;
    private final String suffix;

    private BoardEntry(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public static BoardEntry translateToEntry(String input) {
        String normalized = normalizeSingleLine(input, MAX_LINE_LENGTH);
        if (normalized.isEmpty()) {
            return new BoardEntry("", "");
        }

        if (normalized.length() <= MAX_PART_LENGTH) {
            return new BoardEntry(normalized, "");
        }

        int prefixEnd = safeEnd(normalized, MAX_PART_LENGTH);
        String prefix = normalized.substring(0, prefixEnd);
        String suffixInput = ChatColor.getLastColors(prefix) + normalized.substring(prefixEnd);
        String suffix = normalizeSingleLine(suffixInput, MAX_PART_LENGTH);
        return new BoardEntry(prefix, suffix);
    }

    static String normalizeSingleLine(String input, int maxLength) {
        if (input == null || input.isEmpty() || maxLength <= 0) {
            return "";
        }

        StringBuilder normalized = null;
        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);
            boolean lineBreak = character == '\r' || character == '\n'
                    || character == '\u2028' || character == '\u2029';
            if (!lineBreak) {
                if (Character.isHighSurrogate(character)) {
                    if (index + 1 < input.length() && Character.isLowSurrogate(input.charAt(index + 1))) {
                        if (normalized != null) {
                            normalized.append(character).append(input.charAt(index + 1));
                        }
                        index++;
                        continue;
                    }
                    if (normalized == null) {
                        normalized = new StringBuilder(input.length());
                        normalized.append(input, 0, index);
                    }
                    continue;
                }
                if (Character.isLowSurrogate(character)) {
                    if (normalized == null) {
                        normalized = new StringBuilder(input.length());
                        normalized.append(input, 0, index);
                    }
                    continue;
                }
                if (normalized != null) {
                    normalized.append(character);
                }
                continue;
            }

            if (normalized == null) {
                normalized = new StringBuilder(input.length());
                normalized.append(input, 0, index);
            }
            normalized.append(' ');
            if (character == '\r' && index + 1 < input.length() && input.charAt(index + 1) == '\n') {
                index++;
            }
        }

        String value = normalized == null ? input : normalized.toString();
        int end = safeEnd(value, maxLength);
        return end == value.length() ? value : value.substring(0, end);
    }

    private static int safeEnd(String input, int maxLength) {
        int end = Math.min(input.length(), maxLength);
        for (int index = 0; index < end; index++) {
            char character = input.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= end && index + 1 < input.length()
                        && Character.isLowSurrogate(input.charAt(index + 1))) {
                    return index;
                }
                if (index + 1 < end && Character.isLowSurrogate(input.charAt(index + 1))) {
                    index++;
                }
                continue;
            }
            if (character != ChatColor.COLOR_CHAR) {
                continue;
            }

            int tokenLength = isCompleteHexColor(input, index) ? 14 : 2;
            if (index + tokenLength > end) {
                return index;
            }
            index += tokenLength - 1;
        }
        return end;
    }

    private static boolean isCompleteHexColor(String input, int start) {
        if (start + 14 > input.length() || Character.toLowerCase(input.charAt(start + 1)) != 'x') {
            return false;
        }
        for (int offset = 2; offset < 14; offset += 2) {
            if (input.charAt(start + offset) != ChatColor.COLOR_CHAR
                    || Character.digit(input.charAt(start + offset + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
