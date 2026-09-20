package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class NativeContainerLoot {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;

    public NativeContainerLoot(Target target) {
        level = (ServerLevel) target.world().nativeHandle();
        pos = new BlockPos(target.position().x(), target.position().y(), target.position().z());
        state = (BlockState) target.state().nativeHandle();
    }

    public boolean canonical() {
        return isCanonicalContainer(pos, state);
    }

    public boolean hasNativeTable() {
        return hasNativeLootTable(level, pos, state);
    }

    public boolean matches(NativeBlockState filterState, boolean exact) {
        BlockState filter = (BlockState) filterState.nativeHandle();
        return exact ? filter == state : filter.getBlock() == state.getBlock();
    }

    public Table table(String name) {
        ResourceKey<LootTable> key = resolveNativeKey(name, candidate -> hasNativeTable(level, candidate));
        return key == null ? null : new Table(level.getServer().reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(key).value());
    }

    public Session open() {
        Container container = resolveContainer(level, pos, state);
        return container == null ? null : new Session(container);
    }

    public record Target(NativeWorld world, NativeBlockPoint position, NativeBlockState state) {
    }

    public static final class Table {
        private final LootTable table;

        private Table(LootTable table) {
            this.table = table;
        }
    }

    public final class Session {
        private final Container container;
        private final List<ItemStack> items = new ArrayList<>();
        private LootParams params;

        private Session(Container container) {
            this.container = container;
        }

        public void add(List<NativeItemStack> stacks) {
            NativeItemStack.appendTo(items, stacks);
        }

        public void generate(Table table, long seed) {
            if (params == null) {
                params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .create(LootContextParamSets.CHEST);
            }
            items.addAll(table.table.getRandomItems(params, seed));
        }

        public void fill(RNG rng, Consumer<String> debug) {
            fillContainer(container, items, rng, debug);
        }
    }

    public static ResourceKey<LootTable> resolveNativeKey(String name, Predicate<ResourceKey<LootTable>> registryContains) {
        Identifier id = name == null ? null : Identifier.tryParse(name);
        if (id == null) {
            return null;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        return registryContains.test(key) ? key : null;
    }

    public static void fillContainer(Container container, List<ItemStack> items, RNG rng, Consumer<String> debug) {
        for (ItemStack item : items) {
            addItem(container, item, debug);
        }
        scramble(container, rng);
        container.setChanged();
    }

    private static boolean hasNativeTable(ServerLevel level, ResourceKey<LootTable> key) {
        return level.getServer().reloadableRegistries().lookup()
                .lookup(Registries.LOOT_TABLE)
                .flatMap(registry -> registry.get(key))
                .isPresent();
    }

    public static boolean isCanonicalContainer(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return true;
        }
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        if (connected.equals(pos)) {
            return true;
        }
        return isCanonicalPair(pos, connected);
    }

    public static boolean isCanonicalPair(BlockPos pos, BlockPos connected) {
        return pos.getX() < connected.getX()
                || pos.getX() == connected.getX() && pos.getZ() <= connected.getZ();
    }

    public static boolean hasNativeLootTable(ServerLevel level, BlockPos pos, BlockState state) {
        if (hasNativeLootTableAt(level, pos)) {
            return true;
        }
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return false;
        }
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        return level.hasChunkAt(connected) && hasNativeLootTableAt(level, connected);
    }

    private static boolean hasNativeLootTableAt(ServerLevel level, BlockPos pos) {
        return hasNativeLootTable(level.getBlockEntity(pos));
    }

    public static boolean hasNativeLootTable(BlockEntity blockEntity) {
        return blockEntity instanceof RandomizableContainer container && hasNativeLootTable(container);
    }

    public static boolean hasNativeLootTable(RandomizableContainer container) {
        return container != null && container.getLootTable() != null;
    }

    private static Container resolveContainer(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (combined != null) {
                return combined;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private static void addItem(Container container, ItemStack stack, Consumer<String> debug) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) {
                container.setItem(i, stack.split(container.getMaxStackSize(stack)));
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int limit = container.getMaxStackSize(existing);
                if (existing.getCount() < limit) {
                    int move = Math.min(stack.getCount(), limit - existing.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                }
            }
        }
        if (!stack.isEmpty()) {
            debug.accept("container full, dropped " + stack.getCount() + "x " + stack.getItem());
        }
    }

    private static void scramble(Container container, RNG rng) {
        int size = container.getContainerSize();
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i);
        }
        boolean packedFull = false;

        splitting:
        for (int i = 0; i < items.length; i++) {
            ItemStack is = items[i];

            if (!is.isEmpty() && is.getCount() > 1 && !packedFull) {
                for (int j = 0; j < items.length; j++) {
                    if (items[j].isEmpty()) {
                        int take = rng.nextInt(is.getCount());
                        take = take == 0 ? 1 : take;
                        is.setCount(is.getCount() - take);
                        items[j] = is.copyWithCount(take);
                        continue splitting;
                    }
                }

                packedFull = true;
            }
        }

        for (int i = items.length; i > 1; i--) {
            int j = rng.nextInt(i);
            ItemStack tmp = items[i - 1];
            items[i - 1] = items[j];
            items[j] = tmp;
        }

        for (int i = 0; i < size; i++) {
            container.setItem(i, items[i]);
        }
    }

}
