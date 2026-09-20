package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import java.util.function.Predicate;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;

public final class NativeForgeBootstrap {
    private NativeForgeBootstrap() {
    }

    public static void registerGenerators(FMLJavaModLoadingContext context, NativeChunkGeneratorDefinition definition, String lootKey) {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, definition.namespace());
            chunkGenerators.register(definition.name(), () -> definition.codec());
            chunkGenerators.register(context.getModBusGroup());
            DeferredRegister<MapCodec<? extends IGlobalLootModifier>> lootModifiers = DeferredRegister.create(ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, definition.namespace());
            lootModifiers.register(lootKey, () -> NativeForgeBlockLootModifier.CODEC);
            lootModifiers.register(context.getModBusGroup());
    }

    public static void install(NativeForgeLoader loader, NativeModdedCallbacks callbacks) {
        ServerAboutToStartEvent.BUS.addListener((ServerAboutToStartEvent event) -> callbacks.aboutToStart().accept(NativeModdedServer.fromHandle(event.getServer())));
        ServerStartingEvent.BUS.addListener((ServerStartingEvent event) -> callbacks.start().accept(NativeModdedServer.fromHandle(event.getServer())));
        ServerStartedEvent.BUS.addListener((ServerStartedEvent event) -> callbacks.started().accept(NativeModdedServer.fromHandle(event.getServer())));
        ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) -> callbacks.stop().run());
        LevelEvent.Load.BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.loaded().accept(new ModdedPlatformWorld(level));
            }
        });
        LevelEvent.Unload.BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.unloaded().accept(new ModdedPlatformWorld(level));
            }
        });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.joined().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.disconnected().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        AddPackFindersEvent.BUS.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(callbacks.datapack().get());
            }
        });
        RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent event) -> callbacks.commands().accept(new NativeCommandRegistration(event.getDispatcher())));
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) ->
                event.addNodes(loader.blockBreakPermission()));
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !event.getResult().isDenied()) {
                callbacks.prepareBreak().dispatch(level, player, event.getPos(), event.getState());
            }
        });
        // EventBus 7: the (priority, boolean, consumer) overload's boolean means "always cancels"; passing
        // false is rejected at registration for listeners that never cancel. Use (priority, consumer).
        BlockEvent.EntityPlaceEvent.BUS.addListener(Priority.MONITOR, (BlockEvent.EntityPlaceEvent event) -> {
            if (!(event instanceof BlockEvent.EntityMultiPlaceEvent)
                    && event.getLevel() instanceof ServerLevel level) {
                callbacks.placed().dispatch(level, event.getPos());
            }
        });
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener(
                Priority.MONITOR,
                (BlockEvent.EntityMultiPlaceEvent event) -> {
                    for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
                        if (snapshot.getLevel() instanceof ServerLevel level) {
                            callbacks.placed().dispatch(level, snapshot.getPos());
                        }
                    }
                }
        );
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) (PlayerInteractEvent.LeftClickBlock event) ->
                callbacks.attack().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) (PlayerInteractEvent.RightClickBlock event) ->
                callbacks.use().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post event) -> {
            callbacks.tick().accept(NativeModdedServer.fromHandle(event.server()));
        });
    }
}
