package art.arcane.volmlib.util.format;

import org.bukkit.ChatColor;

import java.util.regex.Pattern;

public final class ColorFormatter {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + ChatColor.COLOR_CHAR + "[0-9A-FK-ORX]");

    private ColorFormatter() {
    }

    public static String stripColor(String input) {
        if (input == null) {
            return null;
        }

        return STRIP_COLOR_PATTERN.matcher(input).replaceAll("");
    }

    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
        if (textToTranslate == null) {
            return null;
        }
        if (textToTranslate.indexOf(altColorChar) < 0) {
            return textToTranslate;
        }

        char[] chars = textToTranslate.toCharArray();
        StringBuilder translated = new StringBuilder(chars.length + 16);
        int length = chars.length;
        for (int i = 0; i < length; i++) {
            char current = chars[i];
            if (current == '\\' && i + 1 < length && chars[i + 1] == altColorChar) {
                translated.append(altColorChar);
                i++;
                continue;
            }
            if (current == altColorChar && i + 1 < length) {
                char next = chars[i + 1];
                if (isLegacyColorCode(next)) {
                    translated.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(next));
                    i++;
                    continue;
                }

                if (next == '#' && i + 7 < length) {
                    String hex = new String(chars, i + 2, 6);
                    if (isHex(hex)) {
                        appendHexColor(translated, hex);
                        i += 7;
                        continue;
                    }
                }

                if ((next == 'x' || next == 'X')) {
                    if (i + 13 < length && isExpandedHex(chars, i, altColorChar)) {
                        StringBuilder hex = new StringBuilder(6);
                        for (int j = 0; j < 6; j++) {
                            hex.append(chars[i + 3 + (j * 2)]);
                        }
                        appendHexColor(translated, hex.toString());
                        i += 13;
                        continue;
                    }

                    if (i + 7 < length) {
                        String hex = new String(chars, i + 2, 6);
                        if (isHex(hex)) {
                            appendHexColor(translated, hex);
                            i += 7;
                            continue;
                        }
                    }
                }
            }
            translated.append(current);
        }

        return translated.toString();
    }

    public static String translateColors(String input) {
        if (input == null || input.indexOf('&') < 0 && input.indexOf('[') < 0) {
            return input;
        }
        String translated = translateAlternateColorCodes('&', input);
        if (translated.length() < 8 || translated.indexOf('[') < 0) {
            return translated;
        }

        StringBuilder target = new StringBuilder(translated.length() + 16);
        for (int index = 0; index < translated.length(); index++) {
            if (translated.charAt(index) == '\\' && index + 1 < translated.length()
                    && translated.charAt(index + 1) == '[') {
                target.append('[');
                index++;
                continue;
            }
            if (translated.charAt(index) == '[' && index + 7 < translated.length()
                    && translated.charAt(index + 7) == ']') {
                String hex = translated.substring(index + 1, index + 7);
                if (isHex(hex)) {
                    appendHexColor(target, hex);
                    index += 7;
                    continue;
                }
            }
            target.append(translated.charAt(index));
        }
        return target.toString();
    }

    public static String getLastColors(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        return ChatColor.getLastColors(input);
    }

    private static boolean isLegacyColorCode(char value) {
        return "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(value) > -1;
    }

    private static boolean isExpandedHex(char[] chars, int offset, char altColorChar) {
        for (int i = 0; i < 6; i++) {
            int ampIndex = offset + 2 + (i * 2);
            int hexIndex = ampIndex + 1;
            if (hexIndex >= chars.length) {
                return false;
            }
            if (chars[ampIndex] != altColorChar || !isHexChar(chars[hexIndex])) {
                return false;
            }
        }
        return true;
    }

    private static void appendHexColor(StringBuilder target, String hex) {
        target.append(ChatColor.COLOR_CHAR).append('x');
        for (int i = 0; i < hex.length(); i++) {
            target.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(hex.charAt(i)));
        }
    }

    private static boolean isHex(String value) {
        if (value == null || value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isHexChar(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexChar(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }
}
