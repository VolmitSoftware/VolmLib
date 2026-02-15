package art.arcane.volmlib.util.director.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class DirectorRuntimeNode {
    private final DirectorNodeDescriptor descriptor;
    private final DirectorRuntimeNode parent;
    private final Object instance;
    private final Method method;
    private final List<DirectorRuntimeNode> children = new ArrayList<>();
    private final List<DirectorRuntimeParameter> parameters;

    public DirectorRuntimeNode(
            DirectorNodeDescriptor descriptor,
            DirectorRuntimeNode parent,
            Object instance,
            Method method,
            List<DirectorRuntimeParameter> parameters
    ) {
        this.descriptor = descriptor;
        this.parent = parent;
        this.instance = instance;
        this.method = method;
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    public DirectorNodeDescriptor getDescriptor() {
        return descriptor;
    }

    public DirectorRuntimeNode getParent() {
        return parent;
    }

    public Object getInstance() {
        return instance;
    }

    public Method getMethod() {
        return method;
    }

    public List<DirectorRuntimeNode> getChildren() {
        return children;
    }

    public List<DirectorRuntimeParameter> getParameters() {
        return parameters;
    }

    public boolean isGroup() {
        return descriptor.isGroup();
    }

    public boolean isInvocable() {
        return method != null;
    }

    public void addChild(DirectorRuntimeNode child) {
        if (child != null) {
            children.add(child);
        }
    }

    public List<String> allNames() {
        List<String> names = new ArrayList<>();
        if (descriptor.getName() != null && !descriptor.getName().trim().isEmpty()) {
            names.add(descriptor.getName());
        }

        for (String alias : descriptor.getAliases()) {
            if (alias != null && !alias.trim().isEmpty()) {
                names.add(alias);
            }
        }

        return names;
    }

    public String path() {
        List<String> parts = new ArrayList<>();
        DirectorRuntimeNode cursor = this;
        while (cursor != null) {
            if (cursor.getDescriptor().getName() != null && !cursor.getDescriptor().getName().isEmpty()) {
                parts.add(cursor.getDescriptor().getName());
            }
            cursor = cursor.getParent();
        }

        StringBuilder out = new StringBuilder("/");
        for (int i = parts.size() - 1; i >= 0; i--) {
            out.append(parts.get(i));
            if (i > 0) {
                out.append(' ');
            }
        }

        return out.toString();
    }
}
