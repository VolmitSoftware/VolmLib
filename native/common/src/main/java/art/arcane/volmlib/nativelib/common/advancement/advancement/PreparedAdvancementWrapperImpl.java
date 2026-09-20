package art.arcane.volmlib.nativelib.common.advancement.advancement;

import art.arcane.volmlib.nativelib.common.advancement.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.PreparedAdvancementWrapper;
import net.minecraft.advancements.AdvancementRequirements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.Map;

public class PreparedAdvancementWrapperImpl extends PreparedAdvancementWrapper {
    private final MinecraftKeyWrapper key;
    private final AdvancementDisplayWrapper display;
    private final Map<String, Object> advCriteria;
    private final AdvancementRequirements advRequirements;

    public PreparedAdvancementWrapperImpl(@NotNull MinecraftKeyWrapper key, @NotNull AdvancementDisplayWrapper display, @Range(from = 1, to = Integer.MAX_VALUE) int maxProgression) {
        this.key = key;
        this.display = display;
        this.advCriteria = Util.getAdvancementCriteria(maxProgression);
        this.advRequirements = Util.getAdvancementRequirements(advCriteria);
    }

    @Override
    @NotNull
    public MinecraftKeyWrapper getKey() {
        return key;
    }

    @Override
    @NotNull
    public AdvancementDisplayWrapper getDisplay() {
        return display;
    }

    @Override
    @Range(from = 1, to = Integer.MAX_VALUE)
    public int getMaxProgression() {
        return advRequirements.size();
    }

    @Override
    @NotNull
    public AdvancementWrapper toRootAdvancementWrapper() {
        return new AdvancementWrapperImpl(key, display, advCriteria, advRequirements);
    }

    @Override
    @NotNull
    public AdvancementWrapper toBaseAdvancementWrapper(@NotNull AdvancementWrapper parent) {
        return new AdvancementWrapperImpl(key, parent, display, advCriteria, advRequirements);
    }
}
