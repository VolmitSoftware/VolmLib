package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeBlockFaces {
    private static final Direction[] CARTESIAN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    public static NativeBlockState fixFaces(NativeBlockState state, Neighbors neighbors) {
        if (!(state instanceof ModdedBlockState fabric)) {
            return state;
        }
        BlockState cloned = fabric.handle();
        Map<String, BooleanProperty> allowed = faceProperties(cloned);

        for (Map.Entry<String, BooleanProperty> entry : allowed.entrySet()) {
            if (cloned.getValue(entry.getValue())) {
                cloned = cloned.setValue(entry.getValue(), Boolean.FALSE);
            }
        }

        boolean found = false;
        for (Direction f : CARTESIAN) {
            NativeBlockState primary = neighbors.primary(f.getStepX(), f.getStepY(), f.getStepZ());
            BlockState r = (BlockState) primary.nativeHandle();
            if (isFaceSturdy(r, f.getOpposite())) {
                BooleanProperty property = allowed.get(f.getSerializedName());
                if (property != null) {
                    found = true;
                    cloned = cloned.setValue(property, Boolean.TRUE);
                }
                continue;
            }

            NativeBlockState secondary = neighbors.secondary(f.getStepX(), f.getStepY(), f.getStepZ());
            if (secondary == null) {
                continue;
            }
            r = (BlockState) secondary.nativeHandle();
            if (isFaceSturdy(r, f.getOpposite())) {
                BooleanProperty property = allowed.get(f.getSerializedName());
                if (property != null) {
                    found = true;
                    cloned = cloned.setValue(property, Boolean.TRUE);
                }
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
