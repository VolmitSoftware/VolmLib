package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureReaderTest {
    @Test
    public void listsOnlyNbtResourcesWithSortedCanonicalTemplateKeys() {
        ResourceManager resources = mock(ResourceManager.class);
        when(resources.listResources(eq("structure"), any())).thenAnswer(invocation -> {
            Predicate<Identifier> selector = invocation.getArgument(1);
            Map<Identifier, Resource> found = new LinkedHashMap<>();
            for (String key : List.of("z:structure/village/house.nbt", "a:structure/start.nbt", "a:structure/readme.txt", "a:structure/.nbt")) {
                Identifier identifier = Identifier.parse(key);
                if (selector.test(identifier)) {
                    found.put(identifier, mock(Resource.class));
                }
            }
            return found;
        });
        assertEquals(List.of("a:start", "z:village/house"), NativeStructureReaderImpl.templates(resources));
    }
}
