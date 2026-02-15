package art.arcane.volmlib.util.director.runtime;

import java.util.ArrayList;
import java.util.List;

public final class DirectorInvocation {
    private final DirectorSender sender;
    private final String label;
    private final List<String> args;

    public DirectorInvocation(DirectorSender sender, String label, List<String> args) {
        this.sender = sender;
        this.label = label;
        this.args = args == null ? List.of() : List.copyOf(args);
    }

    public DirectorSender getSender() {
        return sender;
    }

    public String getLabel() {
        return label;
    }

    public List<String> getArgs() {
        return args;
    }

    public List<String> copyArgs() {
        return new ArrayList<>(args);
    }
}
