package art.arcane.volmlib.integration;

public record IntegrationHeartbeat(
        IntegrationProtocolVersion protocol,
        boolean healthy,
        long lastHeartbeatMs,
        String message
) {
    public IntegrationHeartbeat {
        message = message == null ? "" : message;
    }
}
