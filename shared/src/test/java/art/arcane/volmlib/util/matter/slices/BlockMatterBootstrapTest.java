package art.arcane.volmlib.util.matter.slices;

import art.arcane.volmlib.util.matter.IrisMatter;
import org.bukkit.Bukkit;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BlockMatterBootstrapTest {
    @Before
    public void detachBukkitServer() throws Exception {
        Field server = Bukkit.class.getDeclaredField("server");
        server.setAccessible(true);
        server.set(null, null);
    }

    @Test
    public void initializesMatterRegistryBeforeBukkitServer() {
        assertNull(Bukkit.getServer());
        assertNotNull(new IrisMatter(1, 1, 1));
    }
}
