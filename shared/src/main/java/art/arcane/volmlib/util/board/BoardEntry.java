package art.arcane.volmlib.util.board;

import org.bukkit.ChatColor;

public class BoardEntry {
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
        if (input.isEmpty()) {
            return new BoardEntry("", "");
        }

        if (input.length() <= 16) {
            return new BoardEntry(input, "");
        }

        String prefix = input.substring(0, 16);
        String suffix = "";

        if (prefix.endsWith("\u00a7")) {
            prefix = prefix.substring(0, prefix.length() - 1);
            suffix = "\u00a7" + suffix;
        }

        suffix = left(ChatColor.getLastColors(prefix) + suffix + input.substring(16), 16);
        return new BoardEntry(prefix, suffix);
    }

    private static String left(String input, int len) {
        if (input == null) {
            return null;
        }

        if (len < 0) {
            return "";
        }

        if (input.length() <= len) {
            return input;
        }

        return input.substring(0, len);
    }
}
