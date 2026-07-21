package art.arcane.volmlib.integration;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface IntegrationServiceContract {
    String pluginId();

    String pluginVersion();

    Set<IntegrationProtocolVersion> supportedProtocols();

    Set<String> capabilities();

    Set<IntegrationMetricDescriptor> metricDescriptors();

    IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request);

    IntegrationHeartbeat heartbeat();

    Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys);

    default List<IntegrationMetricGroup> metricGroups() {
        return List.of();
    }
}
