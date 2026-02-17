package art.arcane.volmlib.util.format;

import org.bukkit.ChatColor;

import java.util.regex.Pattern;

public final class ColorFormatter {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)" + ChatColor.COLOR_CHAR + "[0-9A-FK-OR]");

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

        char[] chars = textToTranslate.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(chars[i + 1]) > -1) {
                chars[i] = ChatColor.COLOR_CHAR;
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }

        return new String(chars);
    }

    public static String getLastColors(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        return ChatColor.getLastColors(input);
    }
}
