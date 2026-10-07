package art.arcane.volmlib.util.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class CNGSignedCacheAllocationTest {
    @Test
    public void firstTwoDimensionalSampleAllocatesOnlyItsRootTable() throws Exception {
        ThreadLocal<?> local = cacheLocal();
        local.remove();
        try {
            CNG graph = CNG.signatureDouble(new RNG(17L));
            graph.noiseFastSigned2D(-37.25D, 91.75D);
            Object caches = local.get();
            assertNotNull(field(caches, "owners2D"));
            assertNotNull(field(caches, "entries2D"));
            assertNull(field(caches, "owners3D"));
            assertNull(field(caches, "entries3D"));
            Object first = field(caches, "entries2D");
            graph.noiseFastSigned2D(-37.25D, 91.75D);
            graph.getFracture().scale(0.75D);
            graph.noiseFastSigned2D(-37.25D, 91.75D);
            assertSame(first, field(caches, "entries2D"));
            graph.noiseFastSigned3D(-37.25D, -192.5D, 91.75D);
            assertNotNull(field(caches, "owners3D"));
            assertNotNull(field(caches, "entries3D"));
            assertSame(first, field(caches, "entries2D"));
        } finally {
            local.remove();
        }
    }

    @Test
    public void threeDimensionalFracturesUseSmallTwoDimensionalMemoWithoutRootTable() throws Exception {
        ThreadLocal<?> local = cacheLocal();
        local.remove();
        try {
            CNG graph = CNG.signatureDouble(new RNG(31L));
            graph.noiseFastSigned3D(-37.25D, -192.5D, 91.75D);
            Object caches = local.get();
            assertNotNull(field(caches, "owners3D"));
            assertNotNull(field(caches, "entries3D"));
            assertNull(field(caches, "owners2D"));
            assertNull(field(caches, "entries2D"));
            Object first = field(caches, "entries3D");
            graph.noiseFastSigned2D(-37.25D, 91.75D);
            assertNotNull(field(caches, "owners2D"));
            assertNotNull(field(caches, "entries2D"));
            assertSame(first, field(caches, "entries3D"));
        } finally {
            local.remove();
        }
    }

    private static ThreadLocal<?> cacheLocal() throws Exception {
        Field field = CNG.class.getDeclaredField("SIGNED_CACHES");
        field.setAccessible(true);
        return (ThreadLocal<?>) field.get(null);
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
