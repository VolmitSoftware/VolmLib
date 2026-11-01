package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeBlockFaces {
    private static final Direction[] CARTESIAN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    public static NativeBlockState fixFaces(NativeBlockState state, Neighbors neighbors) {
        if (!(state instanceof ModdedBlockState fabric)) {
            return state;
        }
        BlockState authored = fabric.handle();
        Map<String, BooleanProperty> allowed = faceProperties(authored);
        BlockState kept = keepSupportedSides(authored, allowed, neighbors);
        if (kept != null) {
            return fabric.withHandle(kept, fabric.parsedProperties());
        }

        BlockState cloned = authored;
        for (Map.Entry<String, BooleanProperty> entry : allowed.entrySet()) {
            if (cloned.getValue(entry.getValue())) {
                cloned = cloned.setValue(entry.getValue(), Boolean.FALSE);
            }
        }

        boolean found = false;
        for (Direction f : CARTESIAN) {
            BooleanProperty property = allowed.get(f.getSerializedName());
            if (property != null && sturdy(neighbors, f)) {
                found = true;
                cloned = cloned.setValue(property, Boolean.TRUE);
            }
        }

        if (!found) {
            String fallback = allowed.containsKey("down") ? "down" : "up";
            BooleanProperty property = allowed.get(fallback);
            if (property != null) {
                cloned = cloned.setValue(property, Boolean.TRUE);
            }
        }

        return fabric.withHandle(cloned, fabric.parsedProperties());
    }

    public static boolean canGoOn(NativeBlockState surface, boolean upward) {
        return isFaceSturdy((BlockState) surface.nativeHandle(), upward ? Direction.UP : Direction.DOWN);
    }

    private static BlockState keepSupportedSides(BlockState state, Map<String, BooleanProperty> allowed, Neighbors neighbors) {
        boolean vine = state.is(Blocks.VINE);
        boolean side = false;
        BlockState kept = state;
        for (Direction f : SIDES) {
            BooleanProperty property = allowed.get(f.getSerializedName());
            if (property == null || !state.getValue(property)) {
                continue;
            }
            if (sturdy(neighbors, f) || vine && vineAboveCarries(neighbors, property)) {
                side = true;
            } else {
                kept = kept.setValue(property, Boolean.FALSE);
            }
        }
        if (!side) {
            return null;
        }
        BooleanProperty up = allowed.get("up");
        if (up != null) {
            kept = kept.setValue(up, sturdy(neighbors, Direction.UP));
        }
        BooleanProperty down = allowed.get("down");
        if (down != null && kept.getValue(down) && !sturdy(neighbors, Direction.DOWN)) {
            kept = kept.setValue(down, Boolean.FALSE);
        }
        return kept;
    }

    private static boolean sturdy(Neighbors neighbors, Direction f) {
        Direction attached = f.getOpposite();
        return sturdy(neighbors.primary(f.getStepX(), f.getStepY(), f.getStepZ()), attached)
                || sturdy(neighbors.secondary(f.getStepX(), f.getStepY(), f.getStepZ()), attached);
    }

    private static boolean sturdy(NativeBlockState state, Direction attached) {
        return state != null && isFaceSturdy((BlockState) state.nativeHandle(), attached);
    }

    private static boolean vineAboveCarries(Neighbors neighbors, BooleanProperty property) {
        return carries(neighbors.primary(0, 1, 0), property) || carries(neighbors.secondary(0, 1, 0), property);
    }

    private static boolean carries(NativeBlockState state, BooleanProperty property) {
        return state != null && state.nativeHandle() instanceof BlockState above
                && above.is(Blocks.VINE) && above.getValue(property);
    }

    private static boolean isFaceSturdy(BlockState state, Direction face) {
        return state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face, SupportType.FULL);
    }

    private static Map<String, BooleanProperty> faceProperties(BlockState state) {
        Map<String, BooleanProperty> properties = new LinkedHashMap<>();
        for (String name : FACE_NAMES) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name) && property instanceof BooleanProperty bool) {
                    properties.put(name, bool);
                }
            }
        }
        return properties;
    }
    private NativeBlockFaces() {
    }

    public interface Neighbors {
        NativeBlockState primary(int dx, int dy, int dz);
        NativeBlockState secondary(int dx, int dy, int dz);
    }
}
