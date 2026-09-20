package art.arcane.volmlib.nativelib.common.advancement.advancement;

import art.arcane.volmlib.nativelib.common.advancement.Util;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.MinecraftKeyWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementDisplayWrapper;
import com.fren_gor.ultimateAdvancementAPI.nms.wrappers.advancement.AdvancementWrapper;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Map;
import java.util.Optional;

public class AdvancementWrapperImpl extends AdvancementWrapper {
    private final AdvancementHolder advancementHolder;
    private final MinecraftKeyWrapper key;
    private final AdvancementWrapper parent;
    private final AdvancementDisplayWrapper display;

    public AdvancementWrapperImpl(@NotNull MinecraftKeyWrapper key, @NotNull AdvancementDisplayWrapper display, @Range(from = 1, to = Integer.MAX_VALUE) int maxProgression) {
        Map<String, Object> advCriteria = Util.getAdvancementCriteria(maxProgression);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Advancement advancement = new Advancement(Optional.empty(), Optional.of((DisplayInfo) display.toNMS()), AdvancementRewards.EMPTY, (Map) advCriteria, Util.getAdvancementRequirements(advCriteria), false, Optional.empty());
        this.advancementHolder = new AdvancementHolder((Identifier) key.toNMS(), advancement);
        this.key = key;
        this.parent = null;
        this.display = display;
    }

    public AdvancementWrapperImpl(@NotNull MinecraftKeyWrapper key, @NotNull AdvancementWrapper parent, @NotNull AdvancementDisplayWrapper display, @Range(from = 1, to = Integer.MAX_VALUE) int maxProgression) {
        Map<String, Object> advCriteria = Util.getAdvancementCriteria(maxProgression);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Advancement advancement = new Advancement(Optional.of((Identifier) parent.getKey().toNMS()), Optional.of((DisplayInfo) display.toNMS()), AdvancementRewards.EMPTY, (Map) advCriteria, Util.getAdvancementRequirements(advCriteria), false, Optional.empty());
        this.advancementHolder = new AdvancementHolder((Identifier) key.toNMS(), advancement);
        this.key = key;
        this.parent = parent;
        this.display = display;
    }

    protected AdvancementWrapperImpl(@NotNull MinecraftKeyWrapper key, @NotNull AdvancementDisplayWrapper display, @NotNull Map<String, Object> advCriteria, @NotNull AdvancementRequirements advRequirements) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Advancement advancement = new Advancement(Optional.empty(), Optional.of((DisplayInfo) display.toNMS()), AdvancementRewards.EMPTY, (Map) advCriteria, advRequirements, false, Optional.empty());
        this.advancementHolder = new AdvancementHolder((Identifier) key.toNMS(), advancement);
        this.key = key;
        this.parent = null;
        this.display = display;
    }

    protected AdvancementWrapperImpl(@NotNull MinecraftKeyWrapper key, @NotNull AdvancementWrapper parent, @NotNull AdvancementDisplayWrapper display, @NotNull Map<String, Object> advCriteria, @NotNull AdvancementRequirements advRequirements) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Advancement advancement = new Advancement(Optional.of((Identifier) parent.getKey().toNMS()), Optional.of((DisplayInfo) display.toNMS()), AdvancementRewards.EMPTY, (Map) advCriteria, advRequirements, false, Optional.empty());
        this.advancementHolder = new AdvancementHolder((Identifier) key.toNMS(), advancement);
        this.key = key;
        this.parent = parent;
        this.display = display;
    }

    @Override
    @NotNull
    public MinecraftKeyWrapper getKey() {
        return key;
    }

    @Override
    @Nullable
    public AdvancementWrapper getParent() {
        return parent;
    }

    @Override
    @NotNull
    public AdvancementDisplayWrapper getDisplay() {
        return display;
    }

    @Override
    @Range(from = 1, to = Integer.MAX_VALUE)
    public int getMaxProgression() {
        return advancementHolder.value().requirements().size();
    }

    @Override
    @NotNull
    public AdvancementHolder toNMS() {
        return advancementHolder;
    }
}
