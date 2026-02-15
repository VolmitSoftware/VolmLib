package art.arcane.volmlib.util.director.runtime;

import java.util.List;

public final class DirectorParameterDescriptor {
    private final String name;
    private final String description;
    private final Class<?> type;
    private final boolean required;
    private final boolean contextual;
    private final String defaultValue;
    private final List<String> aliases;

    public DirectorParameterDescriptor(
            String name,
            String description,
            Class<?> type,
            boolean required,
            boolean contextual,
            String defaultValue,
            List<String> aliases
    ) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.required = required;
        this.contextual = contextual;
        this.defaultValue = defaultValue;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isContextual() {
        return contextual;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
