package art.arcane.volmlib.nativelib.common.chunk;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReflectiveChunkPacketAccessTest {
    @Test
    void everyChunkInABurstReusesTheAlreadyResolvedNmsMembersInsteadOfRewalkingTheHierarchy() {
        ReflectiveChunkPacketAccess delivery =
            new ReflectiveChunkPacketAccess(null);

        Method firstHandle = delivery.handleMethod(SampleHandleOwner.class);
        Field firstConnection = delivery.connectionField(SampleConnectionOwner.class);

        assertNotNull(firstHandle);
        assertNotNull(firstConnection);
        for (int chunk = 0; chunk < 49; chunk++) {
            assertSame(firstHandle, delivery.handleMethod(SampleHandleOwner.class),
                "resolving the handle method per chunk would allocate a fresh Method inside the commitment window");
            assertSame(firstConnection, delivery.connectionField(SampleConnectionOwner.class));
        }
    }

    @Test
    void aMemberThatIsAbsentOnThisRuntimeIsRememberedAsAbsent() {
        ReflectiveChunkPacketAccess delivery =
            new ReflectiveChunkPacketAccess(null);

        assertNull(delivery.handleMethod(SampleConnectionOwner.class));
        assertNull(delivery.handleMethod(SampleConnectionOwner.class));
    }

    @Test
    void compatiblePacketConstructorSelectionIsReusedAcrossAChunkBurst() {
        ReflectiveChunkPacketAccess delivery = new ReflectiveChunkPacketAccess(
            SampleChunkPacket.class
        );

        Constructor<?> selected = delivery.packetConstructor(SampleChunk.class, SampleLightEngine.class);

        assertNotNull(selected);
        assertEquals(4, selected.getParameterCount());
        for (int chunk = 0; chunk < 49; chunk++) {
            assertSame(selected, delivery.packetConstructor(SampleChunk.class, SampleLightEngine.class));
        }
        assertNull(delivery.packetConstructor(String.class, SampleLightEngine.class));
    }

    private static final class SampleHandleOwner {
        Object getHandle() {
            return this;
        }
    }

    private static final class SampleConnectionOwner {
        private Object connection;

        Object connection() {
            return connection;
        }
    }

    private static final class SampleChunk {
    }

    private static final class SampleLightEngine {
    }

    private static final class SampleChunkPacket {
        private SampleChunkPacket(SampleChunk chunk, SampleLightEngine lightEngine, Object filter, Object packetData) {
        }

        private SampleChunkPacket(SampleChunk chunk, SampleLightEngine lightEngine, Object filter, Object packetData,
                                  boolean modifyBlocks) {
        }
    }

}
