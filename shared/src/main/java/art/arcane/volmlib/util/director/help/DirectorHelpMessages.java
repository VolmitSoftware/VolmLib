package art.arcane.volmlib.util.director.help;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class DirectorHelpMessages {
    public static final TextKey PARENT_HOVER = TextKey.of("director.help.navigation.parent.hover", "Return to parent command group");
    public static final TextKey BACK = TextKey.of("director.help.navigation.back", "Back");
    public static final TextKey NO_SUBCOMMANDS = TextKey.of("director.help.no_subcommands", "No subcommands on this page.");
    public static final TextKey NO_DESCRIPTION = TextKey.of("director.help.no_description", "No description provided");
    public static final TextKey ALIASES = TextKey.of("director.help.aliases", "Aliases");
    public static final TextKey COMMAND_GROUP = TextKey.of("director.help.command_group", "Command group. Click to open.");
    public static final TextKey NO_PARAMETERS = TextKey.of("director.help.no_parameters", "No parameters. Click to prefill command.");
    public static final TextKey PARAMETERS = TextKey.of("director.help.parameters", "Parameters");
    public static final TextKey REQUIRED = TextKey.of("director.help.parameter.required", "required");
    public static final TextKey OPTIONAL = TextKey.of("director.help.parameter.optional", "optional");
    public static final TextKey DEFAULT = TextKey.of("director.help.parameter.default", "default");
    public static final TextKey PREVIOUS_PAGE = TextKey.of("director.help.navigation.previous.hover", "Previous page");
    public static final TextKey NEXT_PAGE = TextKey.of("director.help.navigation.next.hover", "Next page");
    public static final TextKey PAGE = TextKey.of("director.help.navigation.page", "Page");

    private static final List<MessageKey> KEYS = List.of(
            PARENT_HOVER,
            BACK,
            NO_SUBCOMMANDS,
            NO_DESCRIPTION,
            ALIASES,
            COMMAND_GROUP,
            NO_PARAMETERS,
            PARAMETERS,
            REQUIRED,
            OPTIONAL,
            DEFAULT,
            PREVIOUS_PAGE,
            NEXT_PAGE,
            PAGE
    );

    private DirectorHelpMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
