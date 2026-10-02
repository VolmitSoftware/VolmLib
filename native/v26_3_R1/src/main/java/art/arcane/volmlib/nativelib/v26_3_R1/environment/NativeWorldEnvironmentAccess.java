package art.arcane.volmlib.nativelib.v26_3_R1.environment;

import art.arcane.volmlib.nativelib.environment.WorldEnvironment;
import art.arcane.volmlib.nativelib.environment.WorldEnvironmentAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.joml.Vector3fc;
import org.joml.Vector4fc;

import java.util.Objects;

public final class NativeWorldEnvironmentAccess implements WorldEnvironmentAccess {
    @Override
    public WorldEnvironment sample(World world, Position position) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(position, "position");
        ServerLevel level = ((CraftWorld) world).getHandle();
        Vec3 eye = new Vec3(position.x(), position.y(), position.z());
        EnvironmentAttributeSystem attributes = level.environmentAttributes();
        DimensionType dimension = level.dimensionType();
        WorldEnvironment.Sky sky = new WorldEnvironment.Sky(WorldEnvironment.Skybox.valueOf(dimension.skybox().name()),
            attributes.getValue(EnvironmentAttributes.SUN_ANGLE, eye), attributes.getValue(EnvironmentAttributes.MOON_ANGLE, eye),
            attributes.getValue(EnvironmentAttributes.STAR_ANGLE, eye), attributes.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, eye),
            color(attributes.getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, eye)), color(attributes.getValue(EnvironmentAttributes.SKY_COLOR, eye)),
            attributes.getValue(EnvironmentAttributes.MOON_PHASE, eye).ordinal(), level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
        WorldEnvironment.Fog fog = new WorldEnvironment.Fog(color(attributes.getValue(EnvironmentAttributes.FOG_COLOR, eye)),
            attributes.getValue(EnvironmentAttributes.FOG_START_DISTANCE, eye), attributes.getValue(EnvironmentAttributes.FOG_END_DISTANCE, eye),
            attributes.getValue(EnvironmentAttributes.SKY_FOG_END_DISTANCE, eye), attributes.getValue(EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, eye),
            color(attributes.getValue(EnvironmentAttributes.WATER_FOG_COLOR, eye)), attributes.getValue(EnvironmentAttributes.WATER_FOG_START_DISTANCE, eye),
            attributes.getValue(EnvironmentAttributes.WATER_FOG_END_DISTANCE, eye));
        WorldEnvironment.Lighting lighting = new WorldEnvironment.Lighting(color(attributes.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, eye)),
            attributes.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, eye), color(attributes.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, eye)),
            color(attributes.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, eye)));
        WorldEnvironment.Clouds clouds = new WorldEnvironment.Clouds(color(attributes.getValue(EnvironmentAttributes.CLOUD_COLOR, eye)),
            attributes.getValue(EnvironmentAttributes.CLOUD_HEIGHT, eye));
        WorldEnvironment.Dimension settings = new WorldEnvironment.Dimension(dimension.minY(), dimension.height(), dimension.hasSkyLight(),
            WorldEnvironment.CardinalLighting.valueOf(dimension.cardinalLightType().name()),
            level.isFlat() ? dimension.minY() : 63.0D, dimension.hasEndFlashes());
        return new WorldEnvironment(level.getGameTime(), sky, fog, lighting, clouds, settings);
    }

    private static WorldEnvironment.Color color(Vector3fc value) {
        return new WorldEnvironment.Color(value.x(), value.y(), value.z());
    }

    private static WorldEnvironment.ColorAlpha color(Vector4fc value) {
        return new WorldEnvironment.ColorAlpha(value.x(), value.y(), value.z(), value.w());
    }
}
