package art.arcane.volmlib.nativelib.common.terrain;

import org.bukkit.World;
import org.junit.Test;

import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class RuntimeWorldClockTest {
    @Test
    public void missingWorldHandleSkipsClockWithoutReportingFailure() throws Exception {
        assertUnsupportedClock(mock(World.class));
    }

    @Test
    public void missingClockMethodsSkipClockWithoutReportingFailure() throws Exception {
        assertUnsupportedClock(worldWithHandle(new Object()));
    }

    @Test
    public void supportedClockStillReadsAndWrites() throws Exception {
        ClockHandle handle = new ClockHandle();
        World world = worldWithHandle(handle);
        RuntimeWorldClock backend = new RuntimeWorldClock();

        assertEquals(OptionalLong.of(12000L), backend.readDayTime(world));
        assertTrue(backend.writeDayTime(world, 6000L));
        assertEquals(OptionalLong.of(6000L), backend.readDayTime(world));
    }

    @Test
    public void rejectsFixedDimensionTimeAndAcceptsMutableDimensionTime() throws Exception {
        RuntimeWorldClock clock = new RuntimeWorldClock();
        assertFalse(clock.hasMutableClock(worldWithHandle(new DimensionHandle(true))));
        assertTrue(clock.hasMutableClock(worldWithHandle(new DimensionHandle(false))));
    }

    public record DimensionHandle(boolean fixed) {
        public DimensionHolder dimensionTypeRegistration() {
            return new DimensionHolder(new DimensionType(fixed));
        }
    }

    public record DimensionHolder(DimensionType value) {
    }

    public record DimensionType(boolean hasFixedTime) {
    }

    private void assertUnsupportedClock(World world) throws Exception {
        RuntimeWorldClock backend = new RuntimeWorldClock();
            assertEquals(OptionalLong.empty(), backend.readDayTime(world));
            assertEquals(OptionalLong.empty(), backend.readDayTime(world));
            assertFalse(backend.writeDayTime(world, 6000L));

    }

    private World worldWithHandle(Object handle) {
        World world = mock(World.class, withSettings().extraInterfaces(HandleWorld.class));
        when(((HandleWorld) world).getHandle()).thenReturn(handle);
        return world;
    }

    public interface HandleWorld {
        Object getHandle();
    }

    public static class ClockHandle {
        private long dayTime = 12000L;

        public long getDayTime() {
            return dayTime;
        }

        public void setDayTime(long dayTime) {
            this.dayTime = dayTime;
        }
    }
}
