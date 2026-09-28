package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockVolume;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ChunkDataSectionWriterTest {
    private static final int SECTIONS = 6;
    private static final int ACCESS_MIN_Y = -32;
    private static final int HEIGHT = SECTIONS * 16;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void lowDiversityVolumesMatchThePerBlockWrite() {
        List<BlockState> pool = List.of(
                Blocks.AIR.defaultBlockState(),
                Blocks.CAVE_AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Blocks.SCULK_SENSOR.defaultBlockState(),
                Blocks.MOVING_PISTON.defaultBlockState());
        for (long seed = 1; seed <= 12; seed++) {
            assertEquivalent(seed, random -> pool.get(random), pool.size(), 0.25D, false);
        }
    }

    @Test
    public void wideAndOversizedPalettesMatchThePerBlockWrite() {
        int registrySize = Block.BLOCK_STATE_REGISTRY.size();
        for (int span : new int[]{17, 40, 200, 256, 257, 900}) {
            int offset = 1000;
            assertEquivalent(span, index -> Block.BLOCK_STATE_REGISTRY.byId((offset + index) % registrySize), span, 0.1D, false);
        }
    }

    @Test
    public void uniformAndEmptySectionsMatchThePerBlockWrite() {
        assertEquivalent(7, index -> Blocks.STONE.defaultBlockState(), 1, 0.0D, false);
        assertEquivalent(8, index -> Blocks.AIR.defaultBlockState(), 1, 0.0D, false);
        assertEquivalent(9, index -> Blocks.STONE.defaultBlockState(), 1, 1.0D, false);
    }

    @Test
    public void prepopulatedSectionsMatchThePerBlockWrite() {
        List<BlockState> pool = List.of(
                Blocks.AIR.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(),
                Blocks.WATER.defaultBlockState());
        for (long seed = 20; seed <= 24; seed++) {
            assertEquivalent(seed, random -> pool.get(random), pool.size(), 0.4D, true);
        }
    }

    private static void assertEquivalent(long seed, IntFunction<BlockState> palette, int paletteSize, double nullChance, boolean prepopulate) {
        Random random = new Random(seed);
        int minY = ACCESS_MIN_Y;
        NativeBlockState[] cells = new NativeBlockState[16 * HEIGHT * 16];
        int[] tops = new int[256];
        Map<BlockState, NativeBlockState> handles = new IdentityHashMap<>();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int top = random.nextInt(8) == 0 ? -1 : random.nextInt(HEIGHT);
                tops[(z << 4) | x] = top;
                for (int y = 0; y <= top; y++) {
                    if (random.nextDouble() < nullChance) {
                        continue;
                    }
                    BlockState state = palette.apply(random.nextInt(paletteSize));
                    cells[index(x, y, z)] = handles.computeIfAbsent(state, ChunkDataSectionWriterTest::handle);
                }
                if (top >= 0 && cells[index(x, top, z)] == null) {
                    cells[index(x, top, z)] = handles.computeIfAbsent(Blocks.STONE.defaultBlockState(), ChunkDataSectionWriterTest::handle);
                }
            }
        }
        NativeBlockVolume volume = new NativeBlockVolume() {
            @Override
            public NativeBlockState getStoredRaw(int x, int y, int z) {
                return cells[index(x, y, z)];
            }

            @Override
            public int highestStoredY(int x, int z) {
                return tops[(z << 4) | x];
            }
        };

        FakeChunk legacy = new FakeChunk();
        FakeChunk bulk = new FakeChunk();
        if (prepopulate) {
            Random fill = new Random(seed * 31L);
            for (int i = 0; i < 600; i++) {
                int x = fill.nextInt(16);
                int y = fill.nextInt(HEIGHT);
                int z = fill.nextInt(16);
                BlockState state = fill.nextBoolean() ? Blocks.BARREL.defaultBlockState() : Blocks.CAVE_AIR.defaultBlockState();
                legacy.section(y).setBlockState(x, y & 15, z, state, false);
                bulk.section(y).setBlockState(x, y & 15, z, state, false);
            }
        }

        legacyWrite(legacy.access, volume, minY, 0, HEIGHT);
        assertTrue(new ChunkDataSectionWriter(bulk.access, volume, minY).write(0, HEIGHT));

        for (int sectionIndex = 0; sectionIndex < SECTIONS; sectionIndex++) {
            LevelChunkSection expected = legacy.sections[sectionIndex];
            LevelChunkSection actual = bulk.sections[sectionIndex];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        assertSame("seed " + seed + " section " + sectionIndex + " cell " + x + "," + y + "," + z,
                                expected.getBlockState(x, y, z), actual.getBlockState(x, y, z));
                    }
                }
            }
            assertEquals(expected.hasOnlyAir(), actual.hasOnlyAir());
            assertEquals(expected.hasFluid(), actual.hasFluid());
            assertEquals(expected.isRandomlyTickingBlocks(), actual.isRandomlyTickingBlocks());
            assertEquals(expected.isRandomlyTickingFluids(), actual.isRandomlyTickingFluids());
            assertEquals(expected.moonrise$hasSpecialCollidingBlocks(), actual.moonrise$hasSpecialCollidingBlocks());
            assertEquals(tickingSet(expected), tickingSet(actual));
        }
        assertEquals(legacy.blockEntityLog, bulk.blockEntityLog);
    }

    private static Set<Short> tickingSet(LevelChunkSection section) {
        Set<Short> set = new HashSet<>();
        for (int i = 0; i < section.moonrise$getTickingBlockList().size(); i++) {
            set.add(section.moonrise$getTickingBlockList().getRaw(i));
        }
        return set;
    }

    private static int index(int x, int y, int z) {
        return (z * 16 * HEIGHT) + (y << 4) + x;
    }

    private static NativeBlockState handle(BlockState state) {
        CraftBlockData data = mock(CraftBlockData.class);
        when(data.getState()).thenReturn(state);
        NativeBlockState nativeState = mock(NativeBlockState.class);
        when(nativeState.placementHandle()).thenReturn(data);
        return nativeState;
    }

    private static void legacyWrite(ChunkAccess access, NativeBlockVolume data, int minY, int yStart, int yEnd) {
        int accessMinY = access.getMinY();
        int baseX = access.getPos().getMinBlockX();
        int baseZ = access.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int y = yStart; y < yEnd; y++) {
                int blockY = y + minY;
                LevelChunkSection section = access.getSection((blockY - accessMinY) >> 4);
                int sectionY = blockY & 15;
                for (int x = 0; x < 16; x++) {
                    NativeBlockState platformState = data.getStoredRaw(x, y, z);
                    if (platformState == null) {
                        continue;
                    }
                    BlockState state = ((CraftBlockData) platformState.placementHandle()).getState();
                    if (state.isAir() && section.getBlockState(x, sectionY, z) == state) {
                        continue;
                    }
                    BlockState oldState = section.setBlockState(x, sectionY, z, state, false);
                    if (state.hasBlockEntity()) {
                        BlockPos pos = new BlockPos(baseX + x, blockY, baseZ + z);
                        BlockEntity entity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
                        if (entity == null) {
                            access.removeBlockEntity(pos);
                        } else {
                            access.setBlockEntity(entity);
                        }
                    } else if (oldState != null && oldState.hasBlockEntity()) {
                        access.removeBlockEntity(new BlockPos(baseX + x, blockY, baseZ + z));
                    }
                }
            }
        }
    }

    private static final class FakeChunk {
        private final LevelChunkSection[] sections = new LevelChunkSection[SECTIONS];
        private final List<String> blockEntityLog = new ArrayList<>();
        private final ChunkAccess access = mock(ChunkAccess.class);

        @SuppressWarnings("unchecked")
        private FakeChunk() {
            for (int i = 0; i < SECTIONS; i++) {
                PalettedContainer<BlockState> states = new PalettedContainer<>(
                        Blocks.AIR.defaultBlockState(), Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), null);
                sections[i] = new LevelChunkSection(states, (PalettedContainer<Holder<Biome>>) mock(PalettedContainer.class));
            }
            when(access.getMinY()).thenReturn(ACCESS_MIN_Y);
            when(access.getHeight()).thenReturn(HEIGHT);
            when(access.getPos()).thenReturn(new ChunkPos(3, -7));
            when(access.getSection(anyInt())).thenAnswer(invocation -> sections[(int) invocation.getArgument(0)]);
            when(access.getSectionIndex(anyInt())).thenAnswer(invocation -> ((int) invocation.getArgument(0) - ACCESS_MIN_Y) >> 4);
            doAnswer(invocation -> {
                BlockEntity entity = invocation.getArgument(0);
                blockEntityLog.add("set " + entity.getBlockPos() + " " + entity.getType());
                return null;
            }).when(access).setBlockEntity(any(BlockEntity.class));
            doAnswer(invocation -> {
                blockEntityLog.add("remove " + invocation.getArgument(0));
                return null;
            }).when(access).removeBlockEntity(any(BlockPos.class));
        }

        private LevelChunkSection section(int y) {
            return sections[y >> 4];
        }
    }
}
