package art.arcane.volmlib.nativelib.server;

import art.arcane.volmlib.nativelib.NativeBinding;

@NativeBinding("server.NativeServerDiagnosticsImpl")
public interface NativeServerDiagnostics {
    boolean isCanvas(ClassLoader loader);
    boolean isRaidPersistenceMessage(String message);
    boolean isUnsupportedWorldCreation(Throwable failure);
    boolean isRejectedWorldCreation(Throwable failure);
}
