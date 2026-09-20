package art.arcane.volmlib.nativelib.v26_3_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeBlockColumn;
import java.util.function.UnaryOperator;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

public final class NativeTransitionColumn {
    private NativeTransitionColumn() {
    }

    public static NoiseColumn column(NativeBlockColumn column, LevelHeightAccessor height, UnaryOperator<String> placementKeys) {
        BlockState[] states = new BlockState[height.getHeight()];
        Map<String, BlockState> palette = new HashMap<>();
        for (int offset = 0; offset < states.length; offset++) {
            String state = column.stateKeyAt(height.getMinY() + offset);
            states[offset] = palette.computeIfAbsent(state, key -> parse(key, placementKeys));
        }
        return new NoiseColumn(height.getMinY(), states);
    }

    public static int height(NativeBlockColumn column, Heightmap.Types type, LevelHeightAccessor height, UnaryOperator<String> placementKeys) {
        Map<String, BlockState> palette = new HashMap<>();
        for (int offset = height.getHeight() - 1; offset >= 0; offset--) {
            String state = column.stateKeyAt(height.getMinY() + offset);
            if (type.isOpaque().test(palette.computeIfAbsent(state, key -> parse(key, placementKeys)))) {
                return height.getMinY() + offset + 1;
            }
        }
        return height.getMinY();
    }

    private static BlockState parse(String key, UnaryOperator<String> placementKeys) {
        String nativeKey = placementKeys.apply(key);
        StringReader reader = new StringReader(nativeKey);
        try {
            BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false).blockState();
            if (reader.canRead()) {
                throw new IllegalArgumentException("Unexpected terrain block state suffix: " + reader.getRemaining());
            }
            return state;
        } catch (CommandSyntaxException failure) {
            throw new IllegalArgumentException("Saved terrain block state cannot be resolved: " + key, failure);
        }
    }
}
