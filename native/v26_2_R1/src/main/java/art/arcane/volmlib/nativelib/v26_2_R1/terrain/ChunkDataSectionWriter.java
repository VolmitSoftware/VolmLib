package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockVolume;
import ca.spottedleaf.moonrise.common.list.ShortList;
import ca.spottedleaf.moonrise.patches.collisions.CollisionUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

/**
 * Copies a stored block volume into chunk sections. A section that still holds only plain air is rebuilt in one
 * pass: palette and packed storage are encoded in the network section layout and loaded with
 * {@link PalettedContainer#read}; its block counts are then written from the cell ids already in hand, with the
 * ticking positions in the order {@link LevelChunkSection#recalcBlockCounts} lists them (falling back to that
 * recount when the section's count fields cannot be reached). Any other section takes the per-block path. Block
 * entity changes are replayed afterwards in the per-block visiting order (z, y, x).
 */
final class ChunkDataSectionWriter {
    private static final int SECTION_CELLS = 4096;
    private static final int MAX_LOCAL_PALETTE = 256;
    private static final int UNWRITTEN = -1;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final SectionCounts SECTION_COUNTS = SectionCounts.resolve();

    private final ChunkAccess access;
    private final NativeBlockVolume data;
    private final int minY;
    private final int baseX;
    private final int baseZ;
    private final int[] ids = new int[SECTION_CELLS];
    private final BlockState[] palette = new BlockState[SECTION_CELLS + 1];
    private final Reference2IntOpenHashMap<BlockState> paletteIds = new Reference2IntOpenHashMap<>();
    private final LongArrayList blockEntityCells = new LongArrayList();
    private final int[] cellCounts = new int[MAX_LOCAL_PALETTE + 1];
    private final int[] appearance = new int[MAX_LOCAL_PALETTE + 1];
    private int paletteSize;
    private FriendlyByteBuf buffer;

    ChunkDataSectionWriter(ChunkAccess access, NativeBlockVolume data, int minY) {
        this.access = access;
        this.data = data;
        this.minY = minY;
        this.baseX = access.getPos().getMinBlockX();
        this.baseZ = access.getPos().getMinBlockZ();
        this.paletteIds.defaultReturnValue(UNWRITTEN);
    }

    boolean write(int yStart, int yEnd) {
        int top = Math.min(yEnd - 1, highestStoredY());
        if (top < yStart) {
            return true;
        }

        int accessMinY = access.getMinY();
        int firstSection = (yStart + minY - accessMinY) >> 4;
        int lastSection = (top + minY - accessMinY) >> 4;
        for (int sectionIndex = firstSection; sectionIndex <= lastSection; sectionIndex++) {
            int sectionBlockY = accessMinY + (sectionIndex << 4);
            int from = Math.max(yStart, sectionBlockY - minY);
            int to = Math.min(top + 1, sectionBlockY + 16 - minY);
            if (!writeSection(access.getSection(sectionIndex), sectionBlockY, from, to)) {
                return false;
            }
        }

        applyBlockEntities();
        return true;
    }

    private int highestStoredY() {
        int top = -1;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                top = Math.max(top, data.highestStoredY(x, z));
            }
        }
        return top;
    }

    private boolean writeSection(LevelChunkSection section, int sectionBlockY, int from, int to) {
        paletteIds.clear();
        paletteSize = 0;
        int firstRow = from + minY - sectionBlockY;
        int endRow = to + minY - sectionBlockY;
        Arrays.fill(ids, 0, firstRow << 8, UNWRITTEN);
        Arrays.fill(ids, endRow << 8, SECTION_CELLS, UNWRITTEN);
        boolean written = false;
        boolean unwritten = firstRow > 0 || endRow < 16;
        NativeBlockState lastPlatformState = null;
        int lastId = UNWRITTEN;
        for (int z = 0; z < 16; z++) {
            for (int y = from; y < to; y++) {
                int rowIndex = ((y + minY - sectionBlockY) << 8) | (z << 4);
                for (int x = 0; x < 16; x++) {
                    NativeBlockState platformState = data.getStoredRaw(x, y, z);
                    if (platformState == null) {
                        ids[rowIndex | x] = UNWRITTEN;
                        unwritten = true;
                        continue;
                    }
                    if (platformState != lastPlatformState) {
                        BlockState state = resolve(platformState);
                        if (state == null) {
                            return false;
                        }
                        lastId = paletteId(state);
                        lastPlatformState = platformState;
                    }
                    ids[rowIndex | x] = lastId;
                    written = true;
                }
            }
        }

        if (!written) {
            return true;
        }

        int airId = unwritten ? paletteId(AIR) : UNWRITTEN;
        if (paletteSize > MAX_LOCAL_PALETTE || section.getStates().maybeHas(state -> state != AIR)) {
            writeCells(section, sectionBlockY);
            return true;
        }

        if (paletteSize == 1 && palette[0] == AIR) {
            return true;
        }

        loadSection(section, airId);
        recordBlockEntities(sectionBlockY);
        return true;
    }

    private static BlockState resolve(NativeBlockState platformState) {
        BlockData blockData = (BlockData) platformState.placementHandle();
        return blockData instanceof CraftBlockData craftBlockData ? craftBlockData.getState() : null;
    }

    private int paletteId(BlockState state) {
        int id = paletteIds.getInt(state);
        if (id == UNWRITTEN) {
            id = paletteSize++;
            paletteIds.put(state, id);
            palette[id] = state;
        }
        return id;
    }

    private void loadSection(LevelChunkSection section, int airId) {
        if (buffer == null) {
            buffer = new FriendlyByteBuf(Unpooled.buffer(8 + 5 * (MAX_LOCAL_PALETTE + 1) + SECTION_CELLS));
        }
        buffer.clear();
        int bits = 0;
        if (paletteSize == 1) {
            buffer.writeByte(0);
            buffer.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(palette[0]));
        } else {
            bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
            buffer.writeByte(bits);
            buffer.writeVarInt(paletteSize);
            for (int id = 0; id < paletteSize; id++) {
                buffer.writeVarInt(Block.BLOCK_STATE_REGISTRY.getId(palette[id]));
            }
            int valuesPerLong = 64 / bits;
            for (int start = 0; start < SECTION_CELLS; start += valuesPerLong) {
                int end = Math.min(SECTION_CELLS, start + valuesPerLong);
                long packed = 0L;
                for (int index = start; index < end; index++) {
                    int id = ids[index];
                    packed |= (long) (id == UNWRITTEN ? airId : id) << ((index - start) * bits);
                }
                buffer.writeLong(packed);
            }
        }
        section.getStates().read(buffer);
        if (SECTION_COUNTS == null) {
            section.recalcBlockCounts();
        } else {
            writeBlockCounts(section, airId, bits);
        }
    }

    /**
     * The counts {@link LevelChunkSection#recalcBlockCounts} derives from the loaded storage, taken from the cell ids
     * instead. The recount visits palette ids in the iteration order of a map keyed in first-appearance order and
     * lists each id's positions in index order; only the ticking list depends on that order, so the map is rebuilt
     * the same way only when more than one ticking id has to be ordered.
     */
    private void writeBlockCounts(LevelChunkSection section, int airId, int bits) {
        Arrays.fill(cellCounts, 0, paletteSize, 0);
        int distinct = 0;
        for (int index = 0; index < SECTION_CELLS; index++) {
            int id = ids[index] == UNWRITTEN ? airId : ids[index];
            if (cellCounts[id]++ == 0) {
                appearance[distinct++] = id;
            }
        }

        int nonEmpty = 0;
        int fluids = 0;
        int ticking = 0;
        int tickingFluids = 0;
        int specialColliding = 0;
        int tickingIds = 0;
        int tickingId = UNWRITTEN;
        for (int id = 0; id < paletteSize; id++) {
            int count = cellCounts[id];
            BlockState state = palette[id];
            if (count == 0 || state.isAir()) {
                continue;
            }
            if (CollisionUtil.isSpecialCollidingBlock(state)) {
                specialColliding += count;
            }
            nonEmpty += count;
            if (state.isRandomlyTicking()) {
                ticking += count;
                tickingIds++;
                tickingId = id;
            }
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                fluids += count;
                if (fluid.isRandomlyTicking()) {
                    tickingFluids += count;
                }
            }
        }
        SECTION_COUNTS.write(section, (short) nonEmpty, (short) fluids, (short) ticking, (short) tickingFluids,
                (short) specialColliding);

        ShortList tickingBlocks = section.moonrise$getTickingBlockList();
        tickingBlocks.clear();
        if (tickingIds == 0) {
            return;
        }
        tickingBlocks.setMinCapacity(ticking);
        if (tickingIds == 1) {
            addPositions(tickingBlocks, tickingId);
            return;
        }
        Int2ObjectOpenHashMap<BlockState> recountOrder = new Int2ObjectOpenHashMap<>(bits <= 6 ? 1 << bits : 64);
        for (int index = 0; index < distinct; index++) {
            recountOrder.put(appearance[index], palette[appearance[index]]);
        }
        ObjectIterator<Int2ObjectMap.Entry<BlockState>> entries = recountOrder.int2ObjectEntrySet().fastIterator();
        while (entries.hasNext()) {
            Int2ObjectMap.Entry<BlockState> entry = entries.next();
            if (!entry.getValue().isAir() && entry.getValue().isRandomlyTicking()) {
                addPositions(tickingBlocks, entry.getIntKey());
            }
        }
    }

    private void addPositions(ShortList list, int id) {
        for (int index = 0; index < SECTION_CELLS; index++) {
            if (ids[index] == id) {
                list.add((short) index);
            }
        }
    }

    private void recordBlockEntities(int sectionBlockY) {
        boolean[] blockEntityIds = null;
        for (int id = 0; id < paletteSize; id++) {
            if (palette[id].hasBlockEntity()) {
                if (blockEntityIds == null) {
                    blockEntityIds = new boolean[paletteSize];
                }
                blockEntityIds[id] = true;
            }
        }
        if (blockEntityIds == null) {
            return;
        }
        for (int index = 0; index < SECTION_CELLS; index++) {
            int id = ids[index];
            if (id != UNWRITTEN && blockEntityIds[id]) {
                recordBlockEntity(index & 15, sectionBlockY + (index >> 8), (index >> 4) & 15, false);
            }
        }
    }

    private void writeCells(LevelChunkSection section, int sectionBlockY) {
        for (int index = 0; index < SECTION_CELLS; index++) {
            int id = ids[index];
            if (id == UNWRITTEN) {
                continue;
            }
            BlockState state = palette[id];
            int x = index & 15;
            int y = index >> 8;
            int z = (index >> 4) & 15;
            if (state.isAir() && section.getBlockState(x, y, z) == state) {
                continue;
            }
            BlockState oldState = section.setBlockState(x, y, z, state, false);
            if (state.hasBlockEntity()) {
                recordBlockEntity(x, sectionBlockY + y, z, false);
            } else if (oldState != null && oldState.hasBlockEntity()) {
                recordBlockEntity(x, sectionBlockY + y, z, true);
            }
        }
    }

    private void recordBlockEntity(int x, int blockY, int z, boolean remove) {
        long order = ((long) z << 40) | ((long) (blockY - access.getMinY()) << 8) | ((long) x << 1) | (remove ? 1L : 0L);
        blockEntityCells.add(order);
    }

    private void applyBlockEntities() {
        if (blockEntityCells.isEmpty()) {
            return;
        }

        long[] cells = blockEntityCells.toLongArray();
        Arrays.sort(cells);
        int accessMinY = access.getMinY();
        for (long cell : cells) {
            int z = (int) (cell >>> 40);
            int blockY = (int) ((cell >>> 8) & 0xFFFFFFFFL) + accessMinY;
            int x = (int) ((cell >>> 1) & 15);
            BlockPos pos = new BlockPos(baseX + x, blockY, baseZ + z);
            if ((cell & 1L) != 0L) {
                access.removeBlockEntity(pos);
                continue;
            }
            BlockState state = access.getSection(access.getSectionIndex(blockY)).getBlockState(x, blockY & 15, z);
            BlockEntity entity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
            if (entity == null) {
                access.removeBlockEntity(pos);
            } else {
                access.setBlockEntity(entity);
            }
        }
    }

    /** Write access to the section's private count fields; null when this server build does not have them. */
    private record SectionCounts(VarHandle nonEmpty, VarHandle fluids, VarHandle ticking, VarHandle tickingFluids,
                                 VarHandle specialColliding) {
        private static SectionCounts resolve() {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(LevelChunkSection.class, MethodHandles.lookup());
                return new SectionCounts(
                        lookup.findVarHandle(LevelChunkSection.class, "nonEmptyBlockCount", short.class),
                        lookup.findVarHandle(LevelChunkSection.class, "fluidCount", short.class),
                        lookup.findVarHandle(LevelChunkSection.class, "tickingBlockCount", short.class),
                        lookup.findVarHandle(LevelChunkSection.class, "tickingFluidCount", short.class),
                        lookup.findVarHandle(LevelChunkSection.class, "specialCollidingBlocks", short.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return null;
            }
        }

        private void write(LevelChunkSection section, short nonEmptyCount, short fluidCount, short tickingCount,
                           short tickingFluidCount, short specialCollidingCount) {
            nonEmpty.set(section, nonEmptyCount);
            fluids.set(section, fluidCount);
            ticking.set(section, tickingCount);
            tickingFluids.set(section, tickingFluidCount);
            specialColliding.set(section, specialCollidingCount);
        }
    }
}
