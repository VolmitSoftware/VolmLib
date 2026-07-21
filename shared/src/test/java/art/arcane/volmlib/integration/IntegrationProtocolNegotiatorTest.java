package art.arcane.volmlib.integration;

import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IntegrationProtocolNegotiatorTest {
    @Test
    public void selectsHighestExactCommonVersion() {
        Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
                Set.of(new IntegrationProtocolVersion(1, 0), new IntegrationProtocolVersion(1, 2)),
                Set.of(new IntegrationProtocolVersion(1, 0), new IntegrationProtocolVersion(1, 1), new IntegrationProtocolVersion(1, 2))
        );

        assertEquals(new IntegrationProtocolVersion(1, 2), negotiated.orElseThrow());
    }

    @Test
    public void doesNotSynthesizeUnadvertisedMinorVersion() {
        Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
                Set.of(new IntegrationProtocolVersion(1, 2)),
                Set.of(new IntegrationProtocolVersion(1, 1))
        );

        assertTrue(negotiated.isEmpty());
    }
}
