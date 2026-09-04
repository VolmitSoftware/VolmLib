package art.arcane.volmlib.util.localization;

import java.util.List;

public final class BukkitLanguageMessages {
    public static final TextKey YOUR_LANGUAGE = TextKey.of("language.menu.your_language", "Your language");
    public static final TextKey RESET_YOUR_LANGUAGE = TextKey.of("language.menu.reset_your_language", "Reset your language");
    public static final TextKey SERVER_DEFAULT = TextKey.of("language.menu.server_default", "Server default");
    public static final TextKey EDIT_MESSAGES = TextKey.of("language.menu.edit_messages", "Edit language messages");
    public static final TextKey USE_SERVER_DEFAULT = TextKey.of("language.menu.use_server_default", "Use server default");
    public static final TextKey CURRENT = TextKey.of("language.menu.current", "Current: {locale}");
    public static final TextKey CURRENT_WITH_PERSONAL = TextKey.of(
            "language.menu.current_with_personal", "Current: {locale} (Yours: {personal})");
    public static final TextKey YOUR_DESCRIPTION = TextKey.of(
            "language.menu.your_description", "Change only the messages you see.");
    public static final TextKey RESET_DESCRIPTION = TextKey.of(
            "language.menu.reset_description", "Use the server default language.");
    public static final TextKey SERVER_DESCRIPTION = TextKey.of(
            "language.menu.server_description", "Change the server default language");
    public static final TextKey EDIT_DESCRIPTION = TextKey.of(
            "language.menu.edit_description", "Open the per-language message editor.");
    public static final TextKey SELECT_DESCRIPTION = TextKey.of(
            "language.menu.select_description", "Click to select this language.");
    public static final TextKey REMOVE_PERSONAL_DESCRIPTION = TextKey.of(
            "language.menu.remove_personal_description", "Remove your personal language choice.");
    public static final TextKey NO_CONTROLS = TextKey.of(
            "language.menu.no_controls", "No language controls are available to you.");

    private static final List<MessageKey> KEYS = List.of(
            YOUR_LANGUAGE,
            RESET_YOUR_LANGUAGE,
            SERVER_DEFAULT,
            EDIT_MESSAGES,
            USE_SERVER_DEFAULT,
            CURRENT,
            CURRENT_WITH_PERSONAL,
            YOUR_DESCRIPTION,
            RESET_DESCRIPTION,
            SERVER_DESCRIPTION,
            EDIT_DESCRIPTION,
            SELECT_DESCRIPTION,
            REMOVE_PERSONAL_DESCRIPTION,
            NO_CONTROLS
    );

    private BukkitLanguageMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
