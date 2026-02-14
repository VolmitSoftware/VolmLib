package art.arcane.volmlib.integration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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

        Map<Integer, Integer> localMaxMinorByMajor = toMaxMinorByMajor(local);
        Map<Integer, Integer> remoteMaxMinorByMajor = toMaxMinorByMajor(remote);
        IntegrationProtocolVersion selected = null;

        for (Map.Entry<Integer, Integer> entry : localMaxMinorByMajor.entrySet()) {
            int major = entry.getKey();
            Integer remoteMaxMinor = remoteMaxMinorByMajor.get(major);
            if (remoteMaxMinor == null) {
                continue;
            }

            int negotiatedMinor = Math.min(entry.getValue(), remoteMaxMinor);
            IntegrationProtocolVersion candidate = new IntegrationProtocolVersion(major, negotiatedMinor);
            if (selected == null || candidate.compareTo(selected) > 0) {
                selected = candidate;
            }
        }

        return Optional.ofNullable(selected);
    }

    private static Map<Integer, Integer> toMaxMinorByMajor(Collection<IntegrationProtocolVersion> versions) {
        Map<Integer, Integer> out = new HashMap<>();
        for (IntegrationProtocolVersion version : versions) {
            if (version == null) {
                continue;
            }

            out.merge(version.major(), version.minor(), Math::max);
        }
        return out;
    }
}
