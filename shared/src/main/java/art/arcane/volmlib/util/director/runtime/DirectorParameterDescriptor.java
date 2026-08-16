package art.arcane.volmlib.util.director.runtime;

import java.util.List;

public final class DirectorParameterDescriptor {
    private final String name;
    private final String descriptionKey;
    private final String description;
    private final Class<?> type;
    private final boolean required;
    private final boolean contextual;
    private final boolean contextualOverride;
    private final String defaultValue;
    private final List<String> aliases;

    public DirectorParameterDescriptor(
            String name,
            String descriptionKey,
            String description,
            Class<?> type,
            boolean required,
            boolean contextual,
            boolean contextualOverride,
            String defaultValue,
            List<String> aliases
    ) {
        this.name = name;
        this.descriptionKey = descriptionKey == null ? "" : descriptionKey.trim();
        this.description = description;
        this.type = type;
        this.required = required;
        this.contextual = contextual;
        this.contextualOverride = contextualOverride;
        this.defaultValue = defaultValue;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionKey() {
        return descriptionKey;
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

    public boolean isContextualOverride() {
        return contextualOverride;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
