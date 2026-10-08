package art.arcane.volmlib.integration;

import java.util.Set;

public interface IntegrationSnapshotProvider extends IntegrationServiceContract {
    String CAPABILITY = "metric-snapshots-v1";

    IntegrationMetricSnapshot snapshotMetrics(Set<String> metricKeys);
}
