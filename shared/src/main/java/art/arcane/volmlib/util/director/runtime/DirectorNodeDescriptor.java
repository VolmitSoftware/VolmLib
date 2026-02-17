package art.arcane.volmlib.util.director.runtime;

import art.arcane.volmlib.util.director.DirectorOrigin;

import java.util.List;

public final class DirectorNodeDescriptor {
    private final String name;
    private final String description;
    private final List<String> aliases;
    private final DirectorOrigin origin;
    private final DirectorExecutionMode executionMode;
    private final boolean group;
    private final List<DirectorParameterDescriptor> parameters;

    public DirectorNodeDescriptor(
            String name,
            String description,
            List<String> aliases,
            DirectorOrigin origin,
            DirectorExecutionMode executionMode,
            boolean group,
            List<DirectorParameterDescriptor> parameters
    ) {
        this.name = name;
        this.description = description;
        this.aliases = aliases == null ? List.of() : List.copyOf(aliases);
        this.origin = origin;
        this.executionMode = executionMode;
        this.group = group;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public DirectorOrigin getOrigin() {
        return origin;
    }

    public DirectorExecutionMode getExecutionMode() {
        return executionMode;
    }

    public boolean isGroup() {
        return group;
    }

    public List<DirectorParameterDescriptor> getParameters() {
        return parameters;
    }
}
