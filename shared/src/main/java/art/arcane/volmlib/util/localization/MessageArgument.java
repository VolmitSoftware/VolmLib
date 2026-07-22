package art.arcane.volmlib.util.localization;

import java.util.Objects;

public record MessageArgument(String name, Object value, MessageArgumentKind kind) {
    public MessageArgument {
        name = LocalizationSupport.requirePlaceholderName(name);
        value = Objects.requireNonNull(value, "Message argument value cannot be null");
        kind = Objects.requireNonNull(kind, "Message argument kind cannot be null");
    }

    public static MessageArgument untrusted(String name, Object value) {
        return new MessageArgument(name, value, MessageArgumentKind.UNTRUSTED);
    }

    public static MessageArgument trusted(String name, Object value) {
        return new MessageArgument(name, value, MessageArgumentKind.TRUSTED);
    }
}
