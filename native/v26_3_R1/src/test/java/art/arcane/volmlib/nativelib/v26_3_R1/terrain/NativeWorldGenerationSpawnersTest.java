package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeWorldGenerationSpawnersTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void emptyWorldReceivesVanillaSpawnersUsingItsOwnStorage() throws Exception {
        Fixture fixture = new Fixture(List.of());

        fixture.generation.attachOverworldSpawners(fixture.world);

        List<?> spawners = fixture.spawners();
        assertEquals(List.of(PhantomSpawner.class, PatrolSpawner.class, CatSpawner.class,
                VillageSiege.class, WanderingTraderSpawner.class),
                spawners.stream().map(Object::getClass).toList());
        Field storage = WanderingTraderSpawner.class.getDeclaredField("savedDataStorage");
        storage.setAccessible(true);
        assertSame(fixture.storage, storage.get(spawners.get(4)));
    }

    @Test
    public void existingSpawnersKeepTheirListAndRuntimeState() throws Exception {
        CustomSpawner existing = mock(CustomSpawner.class);
        List<CustomSpawner> spawners = List.of(existing);
        Fixture fixture = new Fixture(spawners);

        fixture.generation.attachOverworldSpawners(fixture.world);

        assertSame(spawners, fixture.spawners());
        assertSame(existing, fixture.spawners().getFirst());
        verify(fixture.level, never()).getDataStorage();
    }

    @Test
    public void repeatedAttachmentKeepsInstalledSpawners() throws Exception {
        Fixture fixture = new Fixture(List.of());
        fixture.generation.attachOverworldSpawners(fixture.world);
        List<?> installed = fixture.spawners();

        fixture.generation.attachOverworldSpawners(fixture.world);

        assertSame(installed, fixture.spawners());
        verify(fixture.level).getDataStorage();
    }

    private static final class Fixture {
        private final NativeWorldGenerationImpl generation = new NativeWorldGenerationImpl();
        private final CraftWorld world = mock(CraftWorld.class);
        private final ServerLevel level = mock(ServerLevel.class);
        private final SavedDataStorage storage = mock(SavedDataStorage.class);
        private final Field customSpawners;

        private Fixture(List<CustomSpawner> spawners) throws ReflectiveOperationException {
            customSpawners = ServerLevel.class.getDeclaredField("customSpawners");
            customSpawners.setAccessible(true);
            customSpawners.set(level, spawners);
            when(world.getHandle()).thenReturn(level);
            when(level.getDataStorage()).thenReturn(storage);
        }

        private List<?> spawners() throws IllegalAccessException {
            return (List<?>) customSpawners.get(level);
        }
    }
}
