package art.arcane.volmlib.util.localization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MessageArgs {
    private static final MessageArgs EMPTY = new MessageArgs(Map.of());

    private final Map<String, MessageArgument> arguments;

    private MessageArgs(Map<String, MessageArgument> arguments) {
        this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }

    public static MessageArgs empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, MessageArgument> arguments() {
        return arguments;
    }

    public Set<String> names() {
        return arguments.keySet();
    }

    public MessageArgument argument(String name) {
        return arguments.get(name);
    }

    public MessageArgument require(String name) {
        String normalizedName = LocalizationSupport.requirePlaceholderName(name);
        MessageArgument argument = arguments.get(normalizedName);
        if (argument == null) {
            throw new IllegalArgumentException("Missing message argument: " + normalizedName);
        }
        return argument;
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    public int size() {
        return arguments.size();
    }

    public static final class Builder {
        private final Map<String, MessageArgument> arguments = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder untrusted(String name, Object value) {
            return add(MessageArgument.untrusted(name, value));
        }

        public Builder trusted(String name, Object value) {
            return add(MessageArgument.trusted(name, value));
        }

        public Builder add(MessageArgument argument) {
            MessageArgument resolved = Objects.requireNonNull(argument, "Message argument cannot be null");
            MessageArgument previous = arguments.putIfAbsent(resolved.name(), resolved);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate message argument: " + resolved.name());
            }
            return this;
        }

        public MessageArgs build() {
            if (arguments.isEmpty()) {
                return EMPTY;
            }
            return new MessageArgs(arguments);
        }
    }
}
