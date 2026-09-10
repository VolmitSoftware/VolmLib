package art.arcane.volmlib.util.config;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class BukkitConfigMessages {
    public static final TextKey TITLE = TextKey.of("config.editor.title", "{plugin} / Configuration");
    public static final TextKey SECTION = TextKey.of("config.editor.section", "Section: {path}");
    public static final TextKey PAGE = TextKey.of("config.editor.page", "Page {page} of {pages}");
    public static final TextKey ENTRY = TextKey.of("config.editor.entry", "Entry {number}");
    public static final TextKey CURRENT = TextKey.of("config.editor.current", "Current: {value}");
    public static final TextKey OPEN = TextKey.of("config.editor.open", "Click to open this section.");
    public static final TextKey TOGGLE = TextKey.of("config.editor.toggle", "Click to toggle true or false.");
    public static final TextKey EDIT = TextKey.of("config.editor.edit", "Click to enter a new value in chat.");
    public static final TextKey TEXT = TextKey.of("config.editor.type.text", "Text; no surrounding quotes needed.");
    public static final TextKey INTEGER = TextKey.of("config.editor.type.integer", "Enter a whole number.");
    public static final TextKey DECIMAL = TextKey.of("config.editor.type.decimal", "Enter a number, such as 0.25.");
    public static final TextKey LIST = TextKey.of("config.editor.type.list", "Use a TOML list, such as [\"stone\", \"deepslate\"].");
    public static final TextKey PROMPT = TextKey.of("config.editor.prompt", "Enter a new value for {path}. Type cancel to return.");
    public static final TextKey EXPIRED = TextKey.of("config.editor.expired", "Configuration input expired.");
    public static final TextKey TOO_LONG = TextKey.of("config.editor.too-long", "Enter at most {maximum} characters.");
    public static final TextKey SAVING = TextKey.of("config.editor.saving", "Saving configuration...");
    public static final TextKey SAVED = TextKey.of("config.editor.saved", "Saved {path}. Changes apply automatically.");
    public static final TextKey FAILED = TextKey.of("config.editor.failed", "Unable to edit configuration: {reason}");
    public static final TextKey LOADING = TextKey.of("config.editor.loading", "Loading configuration...");
    public static final TextKey EMPTY = TextKey.of("config.editor.empty", "This section has no settings.");
    public static final TextKey PLAYER_ONLY = TextKey.of("config.editor.player-only", "Open the configuration editor in game.");
    public static final TextKey NO_PERMISSION = TextKey.of("config.editor.no-permission", "You do not have permission to edit this configuration.");
    public static final TextKey BACK = TextKey.of("config.editor.back", "Back");
    public static final TextKey PREVIOUS = TextKey.of("config.editor.previous", "Previous page");
    public static final TextKey NEXT = TextKey.of("config.editor.next", "Next page");
    public static final TextKey REFRESH = TextKey.of("config.editor.refresh", "Refresh");
    public static final TextKey CLOSE = TextKey.of("config.editor.close", "Close");

    private static final List<MessageKey> KEYS = List.of(TITLE, SECTION, PAGE, ENTRY, CURRENT, OPEN, TOGGLE, EDIT,
            TEXT, INTEGER, DECIMAL, LIST, PROMPT, EXPIRED, TOO_LONG, SAVING, SAVED, FAILED, LOADING, EMPTY,
            PLAYER_ONLY, NO_PERMISSION, BACK, PREVIOUS, NEXT, REFRESH, CLOSE);

    private BukkitConfigMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
