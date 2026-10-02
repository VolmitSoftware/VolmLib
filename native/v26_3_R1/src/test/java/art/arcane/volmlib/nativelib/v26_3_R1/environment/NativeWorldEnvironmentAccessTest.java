package art.arcane.volmlib.nativelib.v26_3_R1.environment;

import art.arcane.volmlib.nativelib.environment.WorldEnvironment;
import art.arcane.volmlib.nativelib.environment.WorldEnvironmentAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;
import org.bukkit.craftbukkit.CraftWorld;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeWorldEnvironmentAccessTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sampleUsesRequestedPositionAndCopiesMutableNativeColors() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        DimensionType dimension = mock(DimensionType.class);
        EnvironmentAttributeSystem attributes = mock(EnvironmentAttributeSystem.class);
        Vec3 eye = new Vec3(23.5, 91.25, -45.75);
        Vector3f color = new Vector3f(0.2f, 0.4f, 0.6f);
        when(world.getHandle()).thenReturn(level);
        when(level.environmentAttributes()).thenReturn(attributes);
        when(level.getFluidState(any(BlockPos.class))).thenReturn(Fluids.EMPTY.defaultFluidState());
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.dimensionType()).thenReturn(dimension);
        when(level.getGameTime()).thenReturn(1234L);
        when(level.getRainLevel(1.0f)).thenReturn(0.25f);
        when(dimension.skybox()).thenReturn(DimensionType.Skybox.OVERWORLD);
        when(dimension.cardinalLightType()).thenReturn(CardinalLighting.Type.DEFAULT);
        when(dimension.minY()).thenReturn(-64);
        when(dimension.height()).thenReturn(384);
        when(attributes.getValue(any(), any(Vec3.class))).thenAnswer(invocation -> {
            assertEquals(eye, invocation.getArgument(1));
            EnvironmentAttribute<?> attribute = invocation.getArgument(0);
            return attribute.defaultValue();
        });
        when(attributes.getValue(eq(EnvironmentAttributes.SKY_COLOR), eq(eye))).thenReturn(color);
        when(attributes.getValue(eq(EnvironmentAttributes.SUN_ANGLE), eq(eye))).thenReturn(90.0f);

        NativeWorldEnvironmentAccess access = new NativeWorldEnvironmentAccess();
        WorldEnvironment result = access.sample(world, new WorldEnvironmentAccess.Position(eye.x, eye.y, eye.z));
        color.set(1.0f, 1.0f, 1.0f);

        assertEquals(new WorldEnvironment.Color(0.2f, 0.4f, 0.6f), result.sky().color());
        assertEquals(90.0f, result.sky().sunAngleDegrees());
        assertEquals(0.25f, result.sky().rain());
        assertEquals(1234L, result.gameTime());
        assertEquals(63.0, result.dimension().horizonHeight());
        when(level.isFlat()).thenReturn(true);
        assertEquals(-64.0, access.sample(world, new WorldEnvironmentAccess.Position(eye.x, eye.y, eye.z)).dimension().horizonHeight());
    }

    @Test
    void samplingFailurePropagatesToTheCaller() {
        CraftWorld world = mock(CraftWorld.class);
        IllegalStateException failure = new IllegalStateException("Destination world unavailable");
        when(world.getHandle()).thenThrow(failure);
        assertEquals(failure, assertThrows(IllegalStateException.class,
            () -> new NativeWorldEnvironmentAccess().sample(world, new WorldEnvironmentAccess.Position(0, 64, 0))));
    }

    @Test
    void samplesTrueEyeMediumAndFixedTimeWithoutInferringDimensionNames() {
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        DimensionType dimension = mock(DimensionType.class);
        EnvironmentAttributeSystem attributes = mock(EnvironmentAttributeSystem.class);
        FluidState fluid = mock(FluidState.class);
        when(world.getHandle()).thenReturn(level);
        when(level.dimensionType()).thenReturn(dimension);
        when(level.environmentAttributes()).thenReturn(attributes);
        when(dimension.skybox()).thenReturn(DimensionType.Skybox.OVERWORLD);
        when(dimension.cardinalLightType()).thenReturn(CardinalLighting.Type.DEFAULT);
        when(dimension.hasFixedTime()).thenReturn(true);
        when(dimension.height()).thenReturn(384);
        when(dimension.logicalHeight()).thenReturn(256);
        when(dimension.hasCeiling()).thenReturn(true);
        when(dimension.ambientLight()).thenReturn(0.1F);
        when(level.getFluidState(any(BlockPos.class))).thenReturn(fluid);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        when(attributes.getValue(any(), any(Vec3.class))).thenAnswer(call -> {
            EnvironmentAttribute<?> attribute = call.getArgument(0);
            return attribute.defaultValue();
        });
        when(fluid.is(FluidTags.WATER)).thenReturn(true);
        when(fluid.getHeightForCamera(eq(level), any(BlockPos.class))).thenReturn(0.25F);
        when(fluid.getHeight(eq(level), any(BlockPos.class))).thenReturn(0.875F);
        NativeWorldEnvironmentAccess access = new NativeWorldEnvironmentAccess();
        assertEquals(WorldEnvironment.EyeMedium.WATER, sample(access, world, 64.249).eyeMedium());
        assertEquals(WorldEnvironment.EyeMedium.NONE, sample(access, world, 64.25).eyeMedium());
        when(fluid.is(FluidTags.WATER)).thenReturn(false);
        when(fluid.is(FluidTags.LAVA)).thenReturn(true);
        assertEquals(WorldEnvironment.EyeMedium.LAVA, sample(access, world, 64.874).eyeMedium());
        assertEquals(WorldEnvironment.EyeMedium.NONE, sample(access, world, 64.875).eyeMedium());
        when(fluid.is(FluidTags.LAVA)).thenReturn(false);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.POWDER_SNOW.defaultBlockState());
        WorldEnvironment snow = sample(access, world, 64.5);
        assertEquals(WorldEnvironment.EyeMedium.POWDER_SNOW, snow.eyeMedium());
        assertTrue(snow.dimension().hasFixedTime());
        assertEquals(256, snow.dimension().logicalHeight());
        assertTrue(snow.dimension().hasCeiling());
        assertEquals(0.1F, snow.dimension().ambientLight());
        assertEquals(384, snow.dimension().height());
        assertEquals(WorldEnvironment.Skybox.OVERWORLD, snow.sky().skybox());
    }

    private static WorldEnvironment sample(NativeWorldEnvironmentAccess access, CraftWorld world, double y) {
        return access.sample(world, new WorldEnvironmentAccess.Position(3.5, y, -4.5));
    }
}
