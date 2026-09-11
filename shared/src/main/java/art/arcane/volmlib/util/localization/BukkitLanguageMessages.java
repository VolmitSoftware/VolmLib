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
    public static final TextKey COMMAND = TextKey.of(
            "language.menu.command", "Command: {command}");
    public static final TextKey SELECTED = TextKey.of(
            "language.menu.selected", "selected");
    public static final TextKey ALL_VOLMIT_PLUGINS = TextKey.of(
            "language.menu.all-volmit-plugins", "all Volmit plugins");
    public static final TextKey STOPPING = TextKey.of(
            "language.error.stopping", "Language selection is unavailable while the plugin is stopping.");
    public static final TextKey EDITOR_PLAYER_ONLY = TextKey.of(
            "language.error.editor-player-only", "Open the language editor in game.");
    public static final TextKey EDITOR_USAGE = TextKey.of(
            "language.usage.editor", "Usage: /{command} language server edit [locale]");
    public static final TextKey SELECTION_USAGE = TextKey.of(
            "language.usage.selection", "Usage: /{command} language self [locale|reset] or server [locale]");
    public static final TextKey VOLMIT_SELECTION_USAGE = TextKey.of(
            "language.usage.volmit-selection", "Usage: /volmit plugins languages [locale]");
    public static final TextKey NO_PROVIDERS = TextKey.of(
            "language.error.no-providers", "No Volmit language providers are available.");
    public static final TextKey NUMERIC_PAGE = TextKey.of(
            "language.error.numeric-page", "Use a numeric language page.");
    public static final TextKey PERSONAL_PLAYER_ONLY = TextKey.of(
            "language.error.personal-player-only", "Player language preferences must be selected in game.");
    public static final TextKey PERSONAL_PERMISSION = TextKey.of(
            "language.error.personal-permission", "You do not have permission to select your language.");
    public static final TextKey PLUGIN_PERSONAL_PERMISSION = TextKey.of(
            "language.error.plugin-personal-permission",
            "You do not have permission to select your language for {plugin}.");
    public static final TextKey PLUGIN_SERVER_PERMISSION = TextKey.of(
            "language.error.plugin-server-permission",
            "You do not have permission to change the server language for {plugin}.");
    public static final TextKey SERVER_LOCALE_REQUIRED = TextKey.of(
            "language.error.server-locale-required", "Select a locale such as en_US for the server default.");
    public static final TextKey UNAVAILABLE_FOR_ALL = TextKey.of(
            "language.error.unavailable-for-all",
            "Language {locale} is not available for every selected plugin.");
    public static final TextKey PREPARING = TextKey.of(
            "language.selection.preparing", "Preparing language {locale} for {target}...");
    public static final TextKey SAVE_FAILED = TextKey.of(
            "language.selection.save-failed",
            "{plugin}: unable to save the language selection; check the server console.");
    public static final TextKey ENGLISH_FALLBACK = TextKey.of(
            "language.selection.english-fallback",
            "{plugin}: {locale} is unavailable; using English (en_US).");
    public static final TextKey PERSONAL_SELECTED = TextKey.of(
            "language.selection.personal-selected", "{plugin}: your language is now {locale}.");
    public static final TextKey SERVER_SELECTED = TextKey.of(
            "language.selection.server-selected", "{plugin}: server language is now {locale}.");
    public static final TextKey SERVER_DEFAULT_SELECTED = TextKey.of(
            "language.selection.server-default-selected", "{plugin}: your language now uses the server default.");
    public static final TextKey EDITOR_TITLE = TextKey.of("language.editor.title", "{plugin} › {section}");
    public static final TextKey EDITOR_LANGUAGES = TextKey.of(
            "language.editor.section.languages", "Languages");
    public static final TextKey EDITOR_SEARCH = TextKey.of(
            "language.editor.section.search", "Search");
    public static final TextKey EDITOR_SECTION_SEARCH = TextKey.of(
            "language.editor.section.search-title", "{locale} / Search");
    public static final TextKey EDITOR_SECTION_GROUP = TextKey.of(
            "language.editor.section.group-title", "{locale} / {group}");
    public static final TextKey EDITOR_LOADING = TextKey.of(
            "language.editor.loading", "Loading {locale} messages...");
    public static final TextKey EDITOR_SEARCH_PROMPT = TextKey.of(
            "language.editor.prompt.search", "Search message keys or text. Type cancel to return.");
    public static final TextKey EDITOR_VALUE_PROMPT = TextKey.of(
            "language.editor.prompt.value", "Type a new value for {key} in chat.");
    public static final TextKey EDITOR_CURRENT_VALUE = TextKey.of(
            "language.editor.prompt.current", "Current Value: {value}");
    public static final TextKey EDITOR_VARIABLES = TextKey.of(
            "language.editor.prompt.variables", "Variables: {variables}");
    public static final TextKey EDITOR_INPUT_GUIDANCE = TextKey.of(
            "language.editor.prompt.guidance",
            "Use \\n for new lines, \\\\ for a backslash, or type cancel to return.");
    public static final TextKey EDITOR_INPUT_EXPIRED = TextKey.of(
            "language.editor.input.expired", "Language editor input expired.");
    public static final TextKey EDITOR_INPUT_TOO_LONG = TextKey.of(
            "language.editor.input.too-long", "Editor input must be at most {maximum} characters.");
    public static final TextKey EDITOR_UNABLE_TO_SAVE = TextKey.of(
            "language.editor.input.unable-to-save", "Unable to save: {reason}");
    public static final TextKey EDITOR_BACK = TextKey.of(
            "language.editor.navigation.back", "&eBack&r");
    public static final TextKey EDITOR_CLOSE = TextKey.of(
            "language.editor.navigation.close", "&cClose&r");
    public static final TextKey EDITOR_PREVIOUS_PAGE = TextKey.of(
            "language.editor.navigation.previous-page", "&ePrevious page&r");
    public static final TextKey EDITOR_NEXT_PAGE = TextKey.of(
            "language.editor.navigation.next-page", "&eNext page&r");
    public static final TextKey EDITOR_SEARCH_MESSAGES = TextKey.of(
            "language.editor.search.messages", "Search messages");
    public static final TextKey EDITOR_SEARCH_MESSAGES_LORE = TextKey.of(
            "language.editor.search.messages-lore", "Click to search all messages.");
    public static final TextKey EDITOR_CLEAR_SEARCH = TextKey.of(
            "language.editor.search.clear", "Clear search");
    public static final TextKey EDITOR_CURRENT_VALUE_LABEL = TextKey.of(
            "language.editor.message.current-value", "&7Current Value:&r");
    public static final TextKey EDITOR_VARIABLE_LIST = TextKey.of(
            "language.editor.message.variables", "&8{variables}&r");
    public static final TextKey EDITOR_EDIT_PLURAL = TextKey.of(
            "language.editor.message.edit-plural", "&7Click to edit a plural form.&r");
    public static final TextKey EDITOR_EDIT_LINES = TextKey.of(
            "language.editor.message.edit-lines", "&7Click to edit individual lines.&r");
    public static final TextKey EDITOR_EDIT_CHAT = TextKey.of(
            "language.editor.message.edit-chat", "&7Click to edit in chat.&r");
    public static final TextKey EDITOR_OPEN_LANGUAGE = TextKey.of(
            "language.editor.locale.open", "&8Click to open this language.&r");
    public static final TextKey EDITOR_MESSAGE_COUNT = TextKey.of(
            "language.editor.category.message-count", "&7{count} messages&r");
    public static final TextKey EDITOR_OPEN_CATEGORY = TextKey.of(
            "language.editor.category.open", "&8Click to open this category.&r");
    public static final TextKey EDITOR_NO_PERMISSION = TextKey.of(
            "language.editor.error.no-permission", "You do not have permission to edit {plugin} languages.");
    public static final TextKey EDITOR_UNABLE_TO_EDIT = TextKey.of(
            "language.editor.error.unable-to-edit", "Unable to edit: {reason}");
    public static final TextKey EDITOR_SAVED = TextKey.of(
            "language.editor.saved.summary", "Saved {locale}. Language selections are unchanged.");
    public static final TextKey EDITOR_CHANGED = TextKey.of(
            "language.editor.saved.change", "{key}: “{before}” changed to “{after}”.");
    public static final TextKey EDITOR_LINE = TextKey.of(
            "language.editor.part.line", "Line {line}");
    public static final TextKey EDITOR_NONE = TextKey.of(
            "language.editor.value.none", "None");
    public static final TextKey EDITOR_EMPTY = TextKey.of(
            "language.editor.value.empty", "(empty)");

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
            NO_CONTROLS,
            COMMAND,
            SELECTED,
            ALL_VOLMIT_PLUGINS,
            STOPPING,
            EDITOR_PLAYER_ONLY,
            EDITOR_USAGE,
            SELECTION_USAGE,
            VOLMIT_SELECTION_USAGE,
            NO_PROVIDERS,
            NUMERIC_PAGE,
            PERSONAL_PLAYER_ONLY,
            PERSONAL_PERMISSION,
            PLUGIN_PERSONAL_PERMISSION,
            PLUGIN_SERVER_PERMISSION,
            SERVER_LOCALE_REQUIRED,
            UNAVAILABLE_FOR_ALL,
            PREPARING,
            SAVE_FAILED,
            ENGLISH_FALLBACK,
            PERSONAL_SELECTED,
            SERVER_SELECTED,
            SERVER_DEFAULT_SELECTED,
            EDITOR_TITLE,
            EDITOR_LANGUAGES,
            EDITOR_SEARCH,
            EDITOR_SECTION_SEARCH,
            EDITOR_SECTION_GROUP,
            EDITOR_LOADING,
            EDITOR_SEARCH_PROMPT,
            EDITOR_VALUE_PROMPT,
            EDITOR_CURRENT_VALUE,
            EDITOR_VARIABLES,
            EDITOR_INPUT_GUIDANCE,
            EDITOR_INPUT_EXPIRED,
            EDITOR_INPUT_TOO_LONG,
            EDITOR_UNABLE_TO_SAVE,
            EDITOR_BACK,
            EDITOR_CLOSE,
            EDITOR_PREVIOUS_PAGE,
            EDITOR_NEXT_PAGE,
            EDITOR_SEARCH_MESSAGES,
            EDITOR_SEARCH_MESSAGES_LORE,
            EDITOR_CLEAR_SEARCH,
            EDITOR_CURRENT_VALUE_LABEL,
            EDITOR_VARIABLE_LIST,
            EDITOR_EDIT_PLURAL,
            EDITOR_EDIT_LINES,
            EDITOR_EDIT_CHAT,
            EDITOR_OPEN_LANGUAGE,
            EDITOR_MESSAGE_COUNT,
            EDITOR_OPEN_CATEGORY,
            EDITOR_NO_PERMISSION,
            EDITOR_UNABLE_TO_EDIT,
            EDITOR_SAVED,
            EDITOR_CHANGED,
            EDITOR_LINE,
            EDITOR_NONE,
            EDITOR_EMPTY
    );

    private BukkitLanguageMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
