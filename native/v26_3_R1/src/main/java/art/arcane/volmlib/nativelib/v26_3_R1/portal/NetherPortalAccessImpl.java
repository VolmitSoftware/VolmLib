package art.arcane.volmlib.nativelib.v26_3_R1.portal;

import art.arcane.volmlib.nativelib.portal.NetherPortalAccess;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import ca.spottedleaf.concurrentutil.util.Priority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class NetherPortalAccessImpl implements NetherPortalAccess {
    @Override
    public CompletableFuture<Void> loadSearch(Location target, int radius) {
        requireOwner();
        validateRadius(radius);
        ServerLevel level = level(target);
        CompletableFuture<Void> loaded = new CompletableFuture<>();
        level.moonrise$loadChunksAsync(position(target), radius + 32, ChunkStatus.EMPTY, Priority.NORMAL,
                chunks -> loaded.complete(null));
        return loaded;
    }

    @Override
    public List<Location> find(Location target, int radius) {
        requireOwner();
        validateRadius(radius);
        ServerLevel level = level(target);
        BlockPos center = position(target);
        List<BlockPos> positions = new ArrayList<>();
        try (Stream<PoiRecord> records = level.getPoiManager().getInSquare(type -> type.is(PoiTypes.NETHER_PORTAL),
                center, radius, PoiManager.Occupancy.ANY)) {
            Iterator<PoiRecord> iterator = records.iterator();
            while (iterator.hasNext()) {
                BlockPos position = iterator.next().getPos();
                if (!level.getWorldBorder().isWithinBounds(position)
                        || level.getTypeKey() == LevelStem.NETHER
                        && level.paperConfig().environment.netherCeilingVoidDamageHeight.test(y -> position.getY() >= y)
                        || !validPortal(level, position)) {
                    continue;
                }
                positions.add(position);
            }
        }
        positions.sort(Comparator.comparingDouble((BlockPos position) -> position.distSqr(center))
                .thenComparingInt(BlockPos::getY));
        List<Location> result = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            result.add(new Location(target.getWorld(), position.getX(), position.getY(), position.getZ()));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<Rectangle> inspect(Location portal) {
        requireOwner();
        ServerLevel level = level(portal);
        BlockPos position = position(portal);
        BlockState state = level.getBlockState(position);
        if (!state.is(Blocks.NETHER_PORTAL)) {
            return Optional.empty();
        }
        Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
        return Optional.of(rectangle(portal, axis, rectangle(level, position, state, axis)));
    }

    @Override
    public Optional<Rectangle> create(Creation creation) {
        requireOwner();
        Location origin = creation.origin();
        Location target = creation.target();
        Direction.Axis axis = level(origin).getBlockState(position(origin))
                .getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        return level(target).getPortalForcer()
                .createPortal(position(target), axis, ((CraftPlayer) creation.player()).getHandle(), creation.radius())
                .map(portal -> rectangle(target, axis, portal));
    }

    @Override
    public Location destination(Player player, Location origin, Rectangle portal) {
        requireOwner();
        ServerPlayer entity = ((CraftPlayer) player).getHandle();
        ServerLevel originLevel = level(origin);
        BlockPos originPosition = position(origin);
        BlockState sourceState = originLevel.getBlockState(originPosition);
        Direction.Axis sourceAxis = sourceState.getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
        Vec3 relative = sourceState.is(Blocks.NETHER_PORTAL)
                ? entity.getRelativePortalPosition(sourceAxis, rectangle(originLevel, originPosition, sourceState, sourceAxis))
                : new Vec3(0.5, 0, 0);
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        double across = dimensions.width() / 2.0 + (portal.width() - dimensions.width()) * relative.x();
        double up = (portal.height() - dimensions.height()) * relative.y();
        double depth = 0.5 + relative.z();
        boolean xAligned = portal.axis() == Axis.X;
        ServerLevel destinationLevel = ((CraftWorld) portal.world()).getHandle();
        Vec3 target = new Vec3(portal.x() + (xAligned ? across : depth), portal.y() + up,
                portal.z() + (xAligned ? depth : across));
        Vec3 safe = PortalShape.findCollisionFreePosition(target, destinationLevel, entity, dimensions);
        float rotation = sourceAxis.name().equals(portal.axis().name()) ? 0 : 90;
        return new Location(portal.world(), safe.x(), safe.y(), safe.z(), origin.getYaw() + rotation, origin.getPitch());
    }

    @Override
    public boolean clearDestination(Player player, Location destination) {
        requireOwner();
        ServerPlayer entity = ((CraftPlayer) player).getHandle();
        ServerLevel level = level(destination);
        AABB bounds = entity.getDimensions(Pose.STANDING).makeBoundingBox(
                new Vec3(destination.getX(), destination.getY(), destination.getZ()));
        return bounds.minY > level.getMinY() && bounds.maxY < level.getMaxY() + 1
                && level.getWorldBorder().isWithinBounds(bounds)
                && level.noCollision(entity, bounds) && !level.containsAnyLiquid(bounds);
    }

    @Override
    public Vector movement(Player player) {
        requireOwner();
        Vec3 movement = ((CraftPlayer) player).getHandle().getKnownMovement();
        return new Vector(movement.x(), movement.y(), movement.z());
    }

    private static void requireOwner() {
        if (!Bukkit.isPrimaryThread() || FoliaScheduler.isFoliaThreading(Bukkit.getServer())) {
            throw new IllegalStateException("Native Nether portal access requires the Paper server thread");
        }
    }

    private static void validateRadius(int radius) {
        if (radius < 0 || radius > 1024) {
            throw new IllegalArgumentException("Portal search radius must be between 0 and 1024");
        }
    }

    private static boolean validPortal(ServerLevel level, BlockPos position) {
        ChunkAccess chunk = level.getChunk(position.getX() >> 4, position.getZ() >> 4, ChunkStatus.EMPTY);
        if (!chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL)) {
            BelowZeroRetrogen retrogen = chunk.getBelowZeroRetrogen();
            if (retrogen == null || !retrogen.targetStatus().isOrAfter(ChunkStatus.SPAWN)) {
                return false;
            }
        }
        return chunk.getBlockState(position).hasProperty(BlockStateProperties.HORIZONTAL_AXIS);
    }

    private static ServerLevel level(Location location) {
        location.checkFinite();
        return ((CraftWorld) location.getWorld()).getHandle();
    }

    private static BlockPos position(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static BlockUtil.FoundRectangle rectangle(ServerLevel level, BlockPos position, BlockState state,
                                                       Direction.Axis axis) {
        return BlockUtil.getLargestRectangleAround(position, axis, 21, Direction.Axis.Y, 21,
                block -> level.getBlockState(block) == state);
    }

    private static Rectangle rectangle(Location location, Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return new Rectangle(location.getWorld(), portal.minCorner.getX(), portal.minCorner.getY(),
                portal.minCorner.getZ(), Axis.valueOf(axis.name()), portal.axis1Size, portal.axis2Size);
    }
}
