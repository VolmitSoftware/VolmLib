package art.arcane.volmlib.util.matter.slices;

import art.arcane.volmlib.util.matter.IrisMatter;
import org.bukkit.Bukkit;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BlockMatterBootstrapTest {
    @Test
    public void initializesMatterRegistryBeforeBukkitServer() {
        assertNull(Bukkit.getServer());
        assertNotNull(new IrisMatter(1, 1, 1));
    }
}
