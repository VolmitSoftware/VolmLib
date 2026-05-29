package art.arcane.volmlib.integration;

public interface ReloadAware {
    void onPreUnload(PreUnloadReason reason);

    enum PreUnloadReason {
        HOT_RELOAD,
        HOT_UNLOAD
    }
}
