package art.arcane.volmlib.nativelib.v26_3_R1.monitor;

import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.bukkit.craftbukkit.CraftWorld;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NativeWorldAccessTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fluidTickUsesCurrentBlockStateAndHonorsFluidSelection() {
        NativeWorldAccess access = new NativeWorldAccessImpl();
        CraftWorld world = Mockito.mock(CraftWorld.class);
        ServerLevel level = Mockito.mock(ServerLevel.class);
        BlockState block = Mockito.mock(BlockState.class);
        FluidState fluid = Mockito.mock(FluidState.class);
        BlockPos position = new BlockPos(2, 64, 3);
        Mockito.when(world.getHandle()).thenReturn(level);
        Mockito.when(level.getBlockState(position)).thenReturn(block);
        Mockito.when(block.getFluidState()).thenReturn(fluid);
        Mockito.when(fluid.is(FluidTags.WATER)).thenReturn(true);

        Assertions.assertFalse(access.tickFluid(world, 2, 64, 3, false, true));
        Mockito.verify(fluid, Mockito.never()).tick(level, position, block);
        Assertions.assertTrue(access.tickFluid(world, 2, 64, 3, true, true));
        Mockito.verify(fluid).tick(level, position, block);
        Mockito.when(fluid.isEmpty()).thenReturn(true);
        Assertions.assertFalse(access.tickFluid(world, 2, 64, 3, true, true));
        Mockito.verify(fluid, Mockito.times(1)).tick(level, position, block);
    }

    @Test
    void hopperAccessUsesCurrentBlockEntityAndNativeCooldown() {
        NativeWorldAccess access = new NativeWorldAccessImpl();
        CraftWorld world = Mockito.mock(CraftWorld.class);
        ServerLevel level = Mockito.mock(ServerLevel.class);
        HopperBlockEntity hopper = Mockito.mock(HopperBlockEntity.class);
        BlockPos position = new BlockPos(2, 64, 3);
        Mockito.when(world.getHandle()).thenReturn(level);
        Assertions.assertNull(access.hopper(world, 2, 64, 3));
        Mockito.when(level.getBlockEntity(position)).thenReturn(hopper);
        Mockito.when(hopper.isEmpty()).thenReturn(true);
        hopper.cooldownTime = 7;

        NativeWorldAccess.HopperAccess handle = access.hopper(world, 2, 64, 3);
        Assertions.assertNotNull(handle);
        Assertions.assertTrue(handle.isEmpty());
        Assertions.assertEquals(7, handle.cooldown());
        handle.cooldown(12);
        Mockito.verify(hopper).setCooldown(12);
    }
}
