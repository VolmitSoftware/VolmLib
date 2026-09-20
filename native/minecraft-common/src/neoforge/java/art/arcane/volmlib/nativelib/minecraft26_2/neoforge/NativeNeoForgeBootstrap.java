package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedPlatformWorld;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import java.util.List;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedCallbacks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockDropHooks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeChunkGeneratorDefinition;

public final class NativeNeoForgeBootstrap {
    private NativeNeoForgeBootstrap() {
    }

    public static void registerGenerator(IEventBus modBus, NativeChunkGeneratorDefinition definition) {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, definition.namespace());
            chunkGenerators.register(definition.name(), () -> definition.codec());
            chunkGenerators.register(modBus);
    }

    public static void install(IEventBus modBus, NativeNeoForgeLoader loader, NativeModdedCallbacks callbacks) {
        modBus.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(callbacks.datapack().get());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> callbacks.aboutToStart().accept(NativeModdedServer.fromHandle(event.getServer())));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> callbacks.start().accept(NativeModdedServer.fromHandle(event.getServer())));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> callbacks.started().accept(NativeModdedServer.fromHandle(event.getServer())));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> callbacks.stop().run());
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.loaded().accept(new ModdedPlatformWorld(level));
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                callbacks.unloaded().accept(new ModdedPlatformWorld(level));
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> callbacks.commands().accept(new NativeCommandRegistration(event.getDispatcher())));
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) ->
                event.addNodes(loader.blockBreakPermission()));
        // LOWEST so every other mod has already had its say about cancelling the break before Iris records
        // provenance for it.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                (BreakBlockEvent event) -> {
                    if (event.getLevel() instanceof ServerLevel level
                            && event.getPlayer() instanceof ServerPlayer player) {
                        callbacks.prepareBreak().dispatch(level, player, event.getPos(), event.getState());
                    }
                }
        );
        // Parity with the Fabric PlayerBlockBreakEvents.CANCELED hook: drop the prepared entry when the break
        // never happens. It only observes cancellations from listeners registered before it at LOWEST; the
        // 1-tick sweeper ModdedBlockBreakHandler.prepare schedules covers every other case.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                true,
                (BreakBlockEvent event) -> {
                    if (event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
                        callbacks.cancelBreak().dispatch(level, event.getPos());
                    }
                }
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                (BlockEvent.EntityPlaceEvent event) -> {
                    if (!(event instanceof BlockEvent.EntityMultiPlaceEvent)
                            && event.getLevel() instanceof ServerLevel level) {
                        callbacks.placed().dispatch(level, event.getPos());
                    }
                }
        );
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                false,
                (BlockEvent.EntityMultiPlaceEvent event) -> {
                    for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
                        if (snapshot.getLevel() instanceof ServerLevel level) {
                            callbacks.placed().dispatch(level, snapshot.getPos());
                        }
                    }
                }
        );
        NeoForge.EVENT_BUS.addListener((BlockDropsEvent event) -> {
            if (!(event.getBreaker() instanceof Player)) {
                return;
            }
            ServerLevel level = event.getLevel();
            NativeBlockDropHooks.Result result = NativeBlockDropHooks.complete(level, event.getPos(), event.getState());
            List<ItemStack> vanillaDrops = event.getDrops().stream()
                    .map(ItemEntity::getItem)
                    .toList();
            if (result.routeCombinedDrops(vanillaDrops)) {
                event.getDrops().clear();
                return;
            }
            if (result.replaceVanillaDrops()) {
                event.getDrops().clear();
            }
            for (ItemStack stack : result.drops()) {
                ItemEntity item = NativeBlockDropHooks.createDrop(level, event.getPos(), stack);
                if (item != null) {
                    event.getDrops().add(item);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (callbacks.attack().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (callbacks.use().dispatch(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            callbacks.tick().accept(NativeModdedServer.fromHandle(event.getServer()));
        });
    }
}
