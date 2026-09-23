package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

final class NativeBlockMaterial {
    private NativeBlockMaterial() {
    }

    static boolean isSolid(Block block) {
        return block.defaultBlockState().is(BlockTags.BLOCKS_MOTION);
    }
}
