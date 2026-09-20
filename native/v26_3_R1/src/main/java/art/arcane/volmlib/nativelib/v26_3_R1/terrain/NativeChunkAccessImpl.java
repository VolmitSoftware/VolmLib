package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeChunkAccess;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

public class NativeChunkAccessImpl implements NativeChunkAccess {
    private static final Logger LOG = Logger.getLogger("VolmLib-Native");

    protected final BlockData AIR = Material.AIR.createBlockData();

    @Override
    public boolean forceEvictChunk(World world, int chunkX, int chunkZ) {
        try {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return true;
            }
            return world.unloadChunk(chunkX, chunkZ, true);
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native chunk operation failed", e);
            return false;
        }
    }

    @Override
    public boolean saveAndUnloadChunk(World world, int x, int z) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            ChunkHolderManager chm = level.moonrise$getChunkTaskScheduler().chunkHolderManager;
            chm.processTicketUpdates();
            NewChunkHolder holder = chm.getChunkHolder(x, z);
            if (holder != null) {
                holder.save(false);
            }
            chm.processUnloads();
            return true;
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native chunk operation failed", e);
            return false;
        }
    }

    @Override
    public boolean pollChunkTask(World world) {
        if (FoliaScheduler.isRegionizedRuntime(Bukkit.getServer())) {
            return false;
        }
        ServerLevel level = ((CraftWorld) world).getHandle();
        if (Thread.currentThread() != level.getServer().getRunningThread()) {
            throw new IllegalStateException("Native chunk tasks require the server lifecycle thread");
        }
        return level.getChunkSource().pollTask();
    }

    @Override
    public void flushChunkIO(World world) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        MoonriseRegionFileIO.flush(level);
    }

    @Override
    public void reconcileNativeStructurePois(Chunk chunk) {
        NativeStructurePoiUpdates.reconcile((LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
    }

    @Override
    public boolean clearChunkBlocks(Chunk bukkitChunk) {
        try {
            ServerLevel level = ((CraftWorld) bukkitChunk.getWorld()).getHandle();
            LevelChunk chunk = level.getChunk(bukkitChunk.getX(), bukkitChunk.getZ());
            removeBlockEntities(chunk);

            BlockState air = ((CraftBlockData) AIR).getState();
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                LevelChunkSection section = chunk.getSection(i);
                if (section.hasOnlyAir()) {
                    continue;
                }

                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            section.setBlockState(x, y, z, air, false);
                        }
                    }
                }
            }

            finishChunkRewrite(level, chunk);
            return true;
        } catch (Throwable e) {
            LOG.log(Level.SEVERE, "Native chunk operation failed", e);
            return false;
        }
    }

    protected void removeBlockEntities(LevelChunk chunk) {
        for (BlockPos pos : new ArrayList<>(chunk.getBlockEntities().keySet())) {
            chunk.removeBlockEntity(pos);
        }
    }

    protected void finishChunkRewrite(ServerLevel level, LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, ChunkStatus.FULL.heightmapsAfter());
        chunk.markUnsaved();
        ThreadedLevelLightEngine lightEngine = (ThreadedLevelLightEngine) level.getChunkSource().getLightEngine();
        lightEngine.starlight$serverRelightChunks(List.of(chunk.getPos()), p -> {
        }, c -> {
        });
    }
}
