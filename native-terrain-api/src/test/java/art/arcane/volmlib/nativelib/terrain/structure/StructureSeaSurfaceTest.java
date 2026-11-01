package art.arcane.volmlib.nativelib.terrain.structure;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class StructureSeaSurfaceTest {
    @Test
    public void noneIsTheOnlySeaSurfaceWithoutAFluid() {
        assertEquals(Integer.MIN_VALUE, StructureSeaSurface.NONE.topY());
        assertNull(StructureSeaSurface.NONE.fluid());
        assertThrows(IllegalStateException.class, () -> new StructureSeaSurface(62, null));
    }
}
