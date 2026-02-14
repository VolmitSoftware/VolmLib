package art.arcane.volmlib.integration;

import java.util.Set;

public record IntegrationHandshakeRequest(
        String requesterPluginId,
        String requesterVersion,
        Set<IntegrationProtocolVersion> supportedProtocols,
        Set<String> capabilities,
        long requestedAtMs
) {
    public IntegrationHandshakeRequest {
        if (requesterPluginId == null || requesterPluginId.isBlank()) {
            throw new IllegalArgumentException("Requester plugin id cannot be blank");
        }

        requesterVersion = requesterVersion == null ? "" : requesterVersion;
        supportedProtocols = supportedProtocols == null ? Set.of() : Set.copyOf(supportedProtocols);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }
}
