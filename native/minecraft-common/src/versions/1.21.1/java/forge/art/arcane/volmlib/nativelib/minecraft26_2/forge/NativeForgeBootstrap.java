package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLifecycle;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedBiomeSource;

public final class NativeForgeBootstrap {
    private NativeForgeBootstrap() {
    }

    public static void registerGenerators(FMLJavaModLoadingContext context, NativeChunkGeneratorDefinition definition, String lootKey) {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, definition.namespace());
            chunkGenerators.register(definition.name(), () -> definition.codec());
            chunkGenerators.register(context.getModEventBus());
            DeferredRegister<MapCodec<? extends BiomeSource>> biomeSources = DeferredRegister.create(Registries.BIOME_SOURCE, definition.namespace());
            biomeSources.register(NativeModdedBiomeSource.STRUCTURE_STATE_TYPE, () -> NativeModdedBiomeSource.STRUCTURE_STATE_CODEC);
            biomeSources.register(context.getModEventBus());
            DeferredRegister<MapCodec<? extends IGlobalLootModifier>> lootModifiers = DeferredRegister.create(ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, definition.namespace());
            lootModifiers.register(lootKey, () -> NativeForgeBlockLootModifier.CODEC);
            lootModifiers.register(context.getModEventBus());
    }

    public static void install(FMLJavaModLoadingContext context, NativeForgeLoader loader, NativeModdedCallbacks callbacks) {
        NativeModdedLifecycle.bind(callbacks.levelsReady());
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            NativeModdedServer server = NativeModdedServer.fromHandle(event.getServer());
            callbacks.aboutToStart().accept(server);
            callbacks.start().accept(server);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callbacks.started().accept(NativeModdedServer.fromHandle(event.getServer())));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> callbacks.stop().run());
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> callbacks.stopped().run());
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.loaded().accept(new ModdedPlatformWorld(level));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.unloaded().accept(new ModdedPlatformWorld(level));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.joined().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callbacks.disconnected().accept(NativeProtocolPlayer.fromHandle(player));
            }
        });
        context.getModEventBus().addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(callbacks.datapack().get());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> callbacks.commands().accept(new NativeCommandRegistration(event.getDispatcher())));
        MinecraftForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) ->
                event.addNodes(loader.blockBreakPermission()));
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !event.isCanceled()) {
                callbacks.prepareBreak().dispatch(level, player, event.getPos(), event.getState());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (BlockEvent.EntityPlaceEvent event) -> {
            if (!(event instanceof BlockEvent.EntityMultiPlaceEvent)
                    && event.getLevel() instanceof ServerLevel level) {
                callbacks.placed().dispatch(level, event.getPos());
            }
        });
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST, false,
                (BlockEvent.EntityMultiPlaceEvent event) -> {
                    for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
                        if (snapshot.getLevel() instanceof ServerLevel level) {
                            callbacks.placed().dispatch(level, snapshot.getPos());
                        }
                    }
                }
        );
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (callbacks.attack().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (callbacks.use().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> {
            callbacks.tick().accept(NativeModdedServer.fromHandle(event.getServer()));
        });
    }
}
