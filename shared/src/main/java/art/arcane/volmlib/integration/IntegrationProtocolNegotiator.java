package art.arcane.volmlib.integration;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class IntegrationProtocolNegotiator {
    private IntegrationProtocolNegotiator() {
    }

    public static Optional<IntegrationProtocolVersion> negotiate(
            Collection<IntegrationProtocolVersion> local,
            Collection<IntegrationProtocolVersion> remote
    ) {
        if (local == null || local.isEmpty() || remote == null || remote.isEmpty()) {
            return Optional.empty();
        }

        Set<IntegrationProtocolVersion> remoteVersions = new HashSet<>(remote);
        IntegrationProtocolVersion selected = null;
        for (IntegrationProtocolVersion version : local) {
            if (version == null || !remoteVersions.contains(version)) {
                continue;
            }
            if (selected == null || version.compareTo(selected) > 0) {
                selected = version;
            }
        }

        return Optional.ofNullable(selected);
    }
}
