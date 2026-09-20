package art.arcane.volmlib.nativelib.minecraft26_2.modded.mixin;

import art.arcane.volmlib.nativelib.modded.NativeMixinFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(StructureTemplate.Palette.class)
public abstract class StructureTemplatePaletteConcurrencyMixin {
    @Shadow
    @Final
    @Mutable
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>(Ljava/util/List;)V", at = @At("RETURN"))
    private void iris$installConcurrentBlockCache(List<StructureTemplate.StructureBlockInfo> blocks,
                                                  CallbackInfo info) {
        cache = new ConcurrentHashMap<>();
        NativeMixinFlags.markStructureTemplatePalette();
    }
}
