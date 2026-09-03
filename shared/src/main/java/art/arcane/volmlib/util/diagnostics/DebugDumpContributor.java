package art.arcane.volmlib.util.diagnostics;

@FunctionalInterface
public interface DebugDumpContributor {
    Report capture();

    @FunctionalInterface
    interface Report {
        String render();
    }
}
