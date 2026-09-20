package art.arcane.volmlib.nativelib.common.player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerClientAccessTest {
  @Test
  void suppressedTagPayloadPreservesEveryOtherBlockTagAndStandardPayload() {
    Identifier climbable = Identifier.withDefaultNamespace("climbable");
    Identifier mineable = Identifier.withDefaultNamespace("mineable/pickaxe");
    IntList climbableIds = new IntArrayList(new int[]{3, 7, 9});
    IntList mineableIds = new IntArrayList(new int[]{11, 12});
    Map<Identifier, IntList> standard = new HashMap<>();
    standard.put(climbable, climbableIds);
    standard.put(mineable, mineableIds);

    Map<Identifier, IntList> suppressed = PlayerClientAccessImpl.suppressBlockInTag(standard, climbable, 7);

    assertThat(suppressed).containsOnlyKeys(climbable, mineable);
    assertThat(suppressed.get(climbable).toIntArray()).containsExactly(3, 9);
    assertThat(suppressed.get(mineable).toIntArray()).containsExactly(11, 12);
    assertThat(standard.get(climbable).toIntArray()).containsExactly(3, 7, 9);
    assertThat(standard.get(mineable).toIntArray()).containsExactly(11, 12);
  }

  @Test
  void suppressingAnUnrelatedBlockFailsInsteadOfSendingAFalseTagView() {
    Identifier climbable = Identifier.withDefaultNamespace("climbable");
    Map<Identifier, IntList> standard = Map.of(climbable, new IntArrayList(new int[]{3, 7, 9}));

    assertThatThrownBy(() -> PlayerClientAccessImpl.suppressBlockInTag(standard, climbable, 12))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("12");
  }

}
