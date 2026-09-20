package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;

public final class NativeFabricBootstrap {
    private NativeFabricBootstrap() {
    }

    public static void registerGenerator(NativeChunkGeneratorDefinition definition) {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(definition.namespace(), definition.name()), definition.codec());
    }

    public static void install(NativeModdedCallbacks callbacks) {
        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> callbacks.start().accept(NativeModdedServer.fromHandle(server)));
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> callbacks.started().accept(NativeModdedServer.fromHandle(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> callbacks.stop().run());
        ServerLevelEvents.LOAD.register((MinecraftServer server, ServerLevel level) -> callbacks.loaded().accept(new ModdedPlatformWorld(level)));
        ServerLevelEvents.UNLOAD.register((MinecraftServer server, ServerLevel level) -> callbacks.unloaded().accept(new ModdedPlatformWorld(level)));
        CommandRegistrationCallback.EVENT.register((CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection) -> callbacks.commands().accept(new NativeCommandRegistration(dispatcher)));
        PlayerBlockBreakEvents.BEFORE.register((Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                callbacks.prepareBreak().dispatch(serverLevel, serverPlayer, pos, state);
            }
            return true;
        });
        PlayerBlockBreakEvents.CANCELED.register((Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                callbacks.cancelBreak().dispatch(serverLevel, pos);
            }
        });
        AttackBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) ->
                callbacks.attack().dispatch(player, level, hand, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockHitResult hit) ->
                callbacks.use().dispatch(player, level, hand, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            callbacks.tick().accept(NativeModdedServer.fromHandle(server));
        });
    }
}
