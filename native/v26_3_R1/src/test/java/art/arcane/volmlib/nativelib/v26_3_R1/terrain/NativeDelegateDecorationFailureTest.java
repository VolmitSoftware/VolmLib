package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativePlacementFailures;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeDelegateDecorationFailureTest {
    private ChunkGenerator delegate;
    private WorldGenLevel level;
    private ChunkAccess chunk;
    private StructureManager manager;
    private final IllegalStateException failure = new IllegalStateException("populator failed");

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Before
    public void setUp() {
        delegate = mock(ChunkGenerator.class);
        level = mock(WorldGenLevel.class);
        chunk = mock(ChunkAccess.class);
        manager = mock(StructureManager.class);
    }

    @After
    public void clearHandler() {
        NativePlacementFailures.register(null);
    }

    @Test
    public void aThrowingDelegateIsSkipped() {
        fail();
        List<String> skipped = new ArrayList<>();
        NativePlacementFailures.register((error, kind, id, x, z) -> skipped.add(kind + " " + x + "," + z));

        NativeChunkGenerator.decorateDelegate(delegate, level, chunk, manager);

        assertEquals(List.of("delegate decoration 32,-16"), skipped);
    }

    @Test
    public void anEngineFailureFromTheDelegateEscapes() {
        fail();
        NativePlacementFailures.register((error, kind, id, x, z) -> {
            throw (IllegalStateException) error;
        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NativeChunkGenerator.decorateDelegate(delegate, level, chunk, manager)));
    }

    @Test
    public void withNoHandlerTheDelegateFailureEscapes() {
        fail();

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> NativeChunkGenerator.decorateDelegate(delegate, level, chunk, manager)));
    }

    private void fail() {
        when(chunk.getPos()).thenReturn(new ChunkPos(2, -1));
        doThrow(failure).when(delegate).applyBiomeDecoration(level, chunk, manager, false);
    }
}
