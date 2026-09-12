package art.arcane.volmlib.util.noise;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class FastNoiseFloorTest {
    @Test
    public void doubleLatticeFloorHandlesNegativeIntegersAndNeighbors() throws Exception {
        Method floor = FastNoiseDouble.class.getDeclaredMethod("fastFloor", double.class);
        floor.setAccessible(true);
        assertEquals(-1L, ((Long) floor.invoke(null, -1D)).longValue());
        assertEquals(-2L, ((Long) floor.invoke(null, -2D)).longValue());
        assertEquals(-2L, ((Long) floor.invoke(null, Math.nextDown(-1D))).longValue());
        assertEquals(-1L, ((Long) floor.invoke(null, Math.nextUp(-1D))).longValue());
        assertEquals(0L, ((Long) floor.invoke(null, -0D)).longValue());
        assertEquals(Long.MIN_VALUE, ((Long) floor.invoke(null, (double) Long.MIN_VALUE)).longValue());
    }

    @Test
    public void floatLatticeFloorHandlesNegativeIntegersAndNeighbors() throws Exception {
        Method floor = FastNoise.class.getDeclaredMethod("FastFloor", float.class);
        floor.setAccessible(true);
        assertEquals(-1, ((Integer) floor.invoke(null, -1F)).intValue());
        assertEquals(-2, ((Integer) floor.invoke(null, -2F)).intValue());
        assertEquals(-2, ((Integer) floor.invoke(null, Math.nextDown(-1F))).intValue());
        assertEquals(-1, ((Integer) floor.invoke(null, Math.nextUp(-1F))).intValue());
        assertEquals(0, ((Integer) floor.invoke(null, -0F)).intValue());
        assertEquals(Integer.MIN_VALUE, ((Integer) floor.invoke(null, (float) Integer.MIN_VALUE)).intValue());
    }
}
