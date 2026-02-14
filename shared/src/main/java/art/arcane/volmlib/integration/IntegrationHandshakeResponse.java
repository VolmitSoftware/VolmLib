package art.arcane.volmlib.integration;

import java.util.Set;

public record IntegrationHandshakeResponse(
        String responderPluginId,
        String responderVersion,
        boolean accepted,
        IntegrationProtocolVersion negotiatedProtocol,
        Set<IntegrationProtocolVersion> supportedProtocols,
        Set<String> capabilities,
        String message,
        long respondedAtMs
) {
    public IntegrationHandshakeResponse {
        if (responderPluginId == null || responderPluginId.isBlank()) {
            throw new IllegalArgumentException("Responder plugin id cannot be blank");
        }

        responderVersion = responderVersion == null ? "" : responderVersion;
        supportedProtocols = supportedProtocols == null ? Set.of() : Set.copyOf(supportedProtocols);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        message = message == null ? "" : message;
    }
}
