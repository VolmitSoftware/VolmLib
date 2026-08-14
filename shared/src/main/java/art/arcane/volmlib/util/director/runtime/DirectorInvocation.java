package art.arcane.volmlib.util.director.runtime;

import java.util.ArrayList;
import java.util.List;

public final class DirectorInvocation {
    private final DirectorSender sender;
    private final String label;
    private final List<String> args;
    private final boolean pretokenized;

    public DirectorInvocation(DirectorSender sender, String label, List<String> args) {
        this(sender, label, args, false);
    }

    private DirectorInvocation(DirectorSender sender, String label, List<String> args, boolean pretokenized) {
        this.sender = sender;
        this.label = label;
        this.args = args == null ? List.of() : List.copyOf(args);
        this.pretokenized = pretokenized;
    }

    public static DirectorInvocation pretokenized(DirectorSender sender, String label, List<String> args) {
        return new DirectorInvocation(sender, label, args, true);
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

    public boolean isPretokenized() {
        return pretokenized;
    }

    public List<String> copyArgs() {
        return new ArrayList<>(args);
    }
}
