package art.arcane.volmlib.util.director.runtime;

import java.util.List;

public interface DirectorCommandEngine {
    DirectorExecutionResult execute(DirectorInvocation invocation);

    List<String> tabComplete(DirectorInvocation invocation);
}
