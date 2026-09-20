package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

public interface NativeStructureReachability {
    boolean isNativeStructureReachable(Holder<Structure> structure);
}
