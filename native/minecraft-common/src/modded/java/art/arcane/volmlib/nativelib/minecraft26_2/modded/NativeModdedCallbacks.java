package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.BlockPos;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record NativeModdedCallbacks(
        Consumer<NativeModdedServer> aboutToStart,
        Consumer<NativeModdedServer> start,
        Consumer<NativeModdedServer> started,
        Runnable stop,
        Consumer<NativeWorld> loaded,
        Consumer<NativeWorld> unloaded,
        Consumer<NativeProtocolPlayer> joined,
        Consumer<NativeProtocolPlayer> disconnected,
        Supplier<NativeDatapackSource> datapack,
        Consumer<NativeCommandRegistration> commands,
        BlockBreakHandler prepareBreak,
        BlockPositionHandler cancelBreak,
        BlockPositionHandler placed,
        BlockInteractionHandler attack,
        BlockInteractionHandler use,
        Consumer<NativeModdedServer> tick
) {
    @FunctionalInterface
    public interface BlockBreakHandler {
        void prepare(NativeBlockBreakContext context);

        default void dispatch(ServerLevel level, ServerPlayer player, BlockPos position, BlockState state) {
            prepare(new NativeBlockBreakContext(new ModdedPlatformWorld(level), NativeProtocolPlayer.fromHandle(player),
                    new NativeBlockPoint(position.getX(), position.getY(), position.getZ()), ModdedBlockState.of(state, null)));
        }
    }

    @FunctionalInterface
    public interface BlockPositionHandler {
        void accept(NativeWorld world, NativeBlockPoint point);

        default void dispatch(ServerLevel level, BlockPos position) {
            accept(new ModdedPlatformWorld(level), new NativeBlockPoint(position.getX(), position.getY(), position.getZ()));
        }
    }

    @FunctionalInterface
    public interface BlockInteractionHandler {
        boolean interact(NativeEditInteraction interaction);

        default boolean dispatch(Player player, Level level, InteractionHand hand, BlockPos position) {
            NativeEditInteraction interaction = NativeEditInteraction.create(player, level, hand, position);
            return interaction != null && interact(interaction);
        }
    }
}
