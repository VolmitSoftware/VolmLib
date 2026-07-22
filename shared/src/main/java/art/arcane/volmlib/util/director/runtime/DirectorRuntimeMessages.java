package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class DirectorRuntimeMessages {
    public static final TextKey INVALID_ORIGIN = TextKey.of(
            "director.runtime.error.invalid_origin",
            "This command cannot be run from this origin."
    );
    public static final TextKey UNKNOWN_PARAMETER = TextKey.of(
            "director.runtime.error.unknown_parameter",
            "Unknown parameter key: {key}"
    );
    public static final TextKey UNEXPECTED_ARGUMENT = TextKey.of(
            "director.runtime.error.unexpected_argument",
            "Unexpected argument \"{argument}\". Optional parameters must be keyed, e.g. seed=123"
    );
    public static final TextKey CONVERSION_FAILED = TextKey.of(
            "director.runtime.error.conversion_failed",
            "Cannot convert \"{value}\" into {type} for {parameter}"
    );
    public static final TextKey DEFAULT_PARSE_FAILED = TextKey.of(
            "director.runtime.error.default_parse_failed",
            "Cannot parse default value for parameter {parameter}"
    );
    public static final TextKey MISSING_ARGUMENT = TextKey.of(
            "director.runtime.error.missing_argument",
            "Missing argument \"{parameter}\" ({type})"
    );
    public static final TextKey EXECUTION_FAILED = TextKey.of(
            "director.runtime.error.execution_failed",
            "Failed to execute command {command}: {reason}"
    );
    public static final TextKey USAGE = TextKey.of(
            "director.runtime.usage",
            "Usage: {usage}"
    );

    private static final List<TextKey> KEYS = List.of(
            INVALID_ORIGIN,
            UNKNOWN_PARAMETER,
            UNEXPECTED_ARGUMENT,
            CONVERSION_FAILED,
            DEFAULT_PARSE_FAILED,
            MISSING_ARGUMENT,
            EXECUTION_FAILED,
            USAGE
    );

    private DirectorRuntimeMessages() {
    }

    public static List<TextKey> keys() {
        return KEYS;
    }
}
