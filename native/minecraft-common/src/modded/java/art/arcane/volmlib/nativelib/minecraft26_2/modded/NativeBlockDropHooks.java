package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class NativeBlockDropHooks {
    private static volatile Hooks hooks;

    private NativeBlockDropHooks() {
    }

    public static void bind(Hooks handlers) {
        hooks = Objects.requireNonNull(handlers, "handlers");
    }

    public static void clearPlacedProvenance(ServerLevel level, BlockPos position) {
        Hooks active = hooks;
        if (active != null) {
            active.placed().accept(new ModdedPlatformWorld(level), point(position));
        }
    }

    public static Result completePrepared(ServerLevel level, BlockPos position) {
        Hooks active = hooks;
        PolicyResult result = active == null ? null : active.complete().apply(new ModdedPlatformWorld(level), point(position));
        return result == null ? null : new NativeResult(result);
    }

    public static Result complete(ServerLevel level, BlockPos position, BlockState state) {
        PolicyResult result = Objects.requireNonNull(hooks, "Block drop hooks have not been bound")
                .completeState().complete(new ModdedPlatformWorld(level), point(position), ModdedBlockState.of(state, null));
        return result == null ? null : new NativeResult(result);
    }

    public static ItemEntity createDrop(ServerLevel level, BlockPos position, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        RandomSource random = level.getRandom();
        double x = position.getX() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D);
        double y = position.getY() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D) - EntityTypes.ITEM.getHeight() / 2D;
        double z = position.getZ() + 0.5D + Mth.nextDouble(random, -0.25D, 0.25D);
        ItemEntity item = new ItemEntity(level, x, y, z, stack);
        item.setDefaultPickUpDelay();
        return item;
    }

    public static void spawn(NativeWorld world, NativeBlockPoint point, Iterable<NativeItemStack> stacks) {
        ServerLevel level = (ServerLevel) world.nativeHandle();
        BlockPos position = new BlockPos(point.x(), point.y(), point.z());
        for (NativeItemStack stack : stacks) {
            ItemEntity item = createDrop(level, position, stack == null ? null : stack.stack());
            if (item != null) {
                level.addFreshEntity(item);
            }
        }
    }

    private static NativeBlockPoint point(BlockPos position) {
        return new NativeBlockPoint(position.getX(), position.getY(), position.getZ());
    }

    @FunctionalInterface
    public interface StateDropHandler {
        PolicyResult complete(NativeWorld level, NativeBlockPoint position, NativeBlockState state);
    }

    public record Hooks(BiConsumer<NativeWorld, NativeBlockPoint> placed,
                        BiFunction<NativeWorld, NativeBlockPoint, ? extends PolicyResult> complete,
                        StateDropHandler completeState) {
        public Hooks {
            Objects.requireNonNull(placed, "placed");
            Objects.requireNonNull(complete, "complete");
            Objects.requireNonNull(completeState, "completeState");
        }
    }

    public interface PolicyResult {
        boolean hasRoute();
        boolean routeCombinedDrops(Iterable<NativeItemStack> vanillaDrops);
        boolean replaceVanillaDrops();
        List<NativeItemStack> drops();
    }

    public interface Result {
        boolean routeCombinedDrops(Iterable<ItemStack> vanillaDrops);
        boolean replaceVanillaDrops();
        List<ItemStack> drops();
    }

    private record NativeResult(PolicyResult policy) implements Result {
        @Override
        public boolean routeCombinedDrops(Iterable<ItemStack> vanillaDrops) {
            if (!policy.hasRoute()) {
                return false;
            }
            List<NativeItemStack> stacks = new ArrayList<>();
            for (ItemStack stack : vanillaDrops) {
                stacks.add(stack == null ? null : new NativeItemStack(stack));
            }
            return policy.routeCombinedDrops(stacks);
        }

        @Override
        public boolean replaceVanillaDrops() {
            return policy.replaceVanillaDrops();
        }

        @Override
        public List<ItemStack> drops() {
            List<NativeItemStack> stacks = policy.drops();
            List<ItemStack> result = new ArrayList<>(stacks.size());
            NativeItemStack.appendTo(result, stacks);
            return result;
        }
    }
}
