package art.arcane.volmlib.nativelib.common.chunk;

import art.arcane.volmlib.nativelib.chunk.ChunkSendRateAccessor;
import art.arcane.volmlib.nativelib.chunk.ChunkSendRateLimit;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveChunkSendRateAccessorTest {
    @Test
    void resolveDegradesToAnUnsupportedNoOpWhenPaperGlobalConfigurationIsAbsent() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.resolve();

        assertFalse(accessor.available());
        assertTrue(accessor.describe().contains(ReflectiveChunkSendRateAccessor.GLOBAL_CONFIGURATION_CLASS));
        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
        assertTrue(accessor.read(ChunkSendRateLimit.LOAD).isEmpty());
        assertFalse(accessor.write(ChunkSendRateLimit.SEND, 1000.0D));
    }

    @Test
    void resolveDegradesToAnUnsupportedNoOpWhenTheClassLoaderCannotSeeTheClass() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.resolve(ClassLoader.getPlatformClassLoader());

        assertFalse(accessor.available());
        assertTrue(accessor.describe().contains("absent"));
    }

    @Test
    void bindsPaperShapedPublicDoubleFieldsAndWritesThrough() {
        PaperShapedConfiguration configuration = new PaperShapedConfiguration();

        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(configuration, "fake");

        assertTrue(accessor.available());
        assertEquals(OptionalDouble.of(75.0D), accessor.read(ChunkSendRateLimit.SEND));
        assertEquals(OptionalDouble.of(100.0D), accessor.read(ChunkSendRateLimit.LOAD));
        assertTrue(accessor.write(ChunkSendRateLimit.SEND, 1000.0D));
        assertEquals(1000.0D, configuration.chunkLoadingBasic.playerMaxChunkSendRate);
        assertEquals(OptionalDouble.of(1000.0D), accessor.read(ChunkSendRateLimit.SEND));
    }

    @Test
    void renamedLimitFieldReadsEmptyAndRefusesWrites() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(new RenamedFieldConfiguration(), "fake");

        assertTrue(accessor.available());
        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
        assertTrue(accessor.read(ChunkSendRateLimit.LOAD).isEmpty());
        assertFalse(accessor.write(ChunkSendRateLimit.SEND, 1000.0D));
    }

    @Test
    void renamedSectionReadsEmpty() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(new RenamedSectionConfiguration(), "fake");

        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
        assertTrue(accessor.read(ChunkSendRateLimit.LOAD).isEmpty());
    }

    @Test
    void nullSectionReadsEmpty() {
        PaperShapedConfiguration configuration = new PaperShapedConfiguration();
        configuration.chunkLoadingBasic = null;

        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(configuration, "fake");

        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
    }

    @Test
    void retypedLimitFieldIsRejected() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(new RetypedFieldConfiguration(), "fake");

        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
    }

    @Test
    void finalLimitFieldIsRejected() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(new FinalFieldConfiguration(), "fake");

        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
        assertFalse(accessor.write(ChunkSendRateLimit.SEND, 1000.0D));
    }

    @Test
    void nonPublicLimitFieldIsRejected() {
        ChunkSendRateAccessor accessor = ReflectiveChunkSendRateAccessor.bind(new HiddenFieldConfiguration(), "fake");

        assertTrue(accessor.read(ChunkSendRateLimit.SEND).isEmpty());
    }

    public static final class PaperShapedConfiguration {
        public ChunkLoadingBasic chunkLoadingBasic = new ChunkLoadingBasic();
    }

    public static final class ChunkLoadingBasic {
        public double playerMaxChunkSendRate = 75.0D;
        public double playerMaxChunkLoadRate = 100.0D;
    }

    public static final class RenamedFieldConfiguration {
        public RenamedFields chunkLoadingBasic = new RenamedFields();
    }

    public static final class RenamedFields {
        public double playerChunkSendRate = 75.0D;
        public double playerChunkLoadRate = 100.0D;
    }

    public static final class RenamedSectionConfiguration {
        public ChunkLoadingBasic chunkLoadingSimple = new ChunkLoadingBasic();
    }

    public static final class RetypedFieldConfiguration {
        public RetypedFields chunkLoadingBasic = new RetypedFields();
    }

    public static final class RetypedFields {
        public int playerMaxChunkSendRate = 75;
        public double playerMaxChunkLoadRate = 100.0D;
    }

    public static final class FinalFieldConfiguration {
        public FinalFields chunkLoadingBasic = new FinalFields();
    }

    public static final class FinalFields {
        public final double playerMaxChunkSendRate = 75.0D;
        public double playerMaxChunkLoadRate = 100.0D;
    }

    public static final class HiddenFieldConfiguration {
        public HiddenFields chunkLoadingBasic = new HiddenFields();
    }

    public static final class HiddenFields {
        private double playerMaxChunkSendRate = 75.0D;
        public double playerMaxChunkLoadRate = 100.0D;

        public double sendRate() {
            return playerMaxChunkSendRate;
        }
    }
}
