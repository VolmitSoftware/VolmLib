package art.arcane.volmlib.nativelib.v26_2_R1.monitor;

import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class NativeWorldAccessImpl implements NativeWorldAccess {
    @Override
    public EntityRangeSettings entityRanges(World world) {
        return new NativeEntityRangeSettings(((CraftWorld) world).getHandle().spigotConfig);
    }

    @Override
    public HopperAccess hopper(World world, int x, int y, int z) {
        BlockEntity blockEntity = ((CraftWorld) world).getHandle().getBlockEntity(new BlockPos(x, y, z));
        return blockEntity instanceof HopperBlockEntity hopper ? new NativeHopper(hopper) : null;
    }

    @Override
    public boolean tickFluid(World world, int x, int y, int z, boolean water, boolean lava) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        BlockPos position = new BlockPos(x, y, z);
        BlockState block = level.getBlockState(position);
        FluidState fluid = block.getFluidState();
        if (fluid.isEmpty() || (!water && fluid.is(FluidTags.WATER)) || (!lava && fluid.is(FluidTags.LAVA))) {
            return false;
        }
        fluid.tick(level, position, block);
        return true;
    }

    @Override
    public boolean setNavigationBudget(Mob mob, float multiplier) {
        ((CraftMob) mob).getHandle().getNavigation().setMaxVisitedNodesMultiplier(multiplier);
        return true;
    }

    @Override
    public void resetNavigationBudget(Mob mob) {
        ((CraftMob) mob).getHandle().getNavigation().resetMaxVisitedNodesMultiplier();
    }

    @Override
    public void sendCollectPacket(Player player, int entityId, int collectorId, int amount) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundTakeItemEntityPacket(entityId, collectorId, amount));
    }

    private record NativeHopper(HopperBlockEntity handle) implements HopperAccess {
        @Override
        public boolean addItem(Item item) {
            return HopperBlockEntity.addItem(handle, ((CraftItem) item).getHandle());
        }

        @Override
        public boolean isEmpty() {
            return handle.isEmpty();
        }

        @Override
        public int cooldown() {
            return handle.cooldownTime;
        }

        @Override
        public void cooldown(int ticks) {
            handle.setCooldown(ticks);
        }
    }
}
