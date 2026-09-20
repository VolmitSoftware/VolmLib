package art.arcane.volmlib.nativelib.common.structure;

import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StructureMetadataReadTest {
    @Test
    public void optionalNbtValuesAndSignedPrioritiesRemainUnchanged() throws Exception {
        String state = "minecraft:oak_stairs[facing=east,half=top]";
        NativeStructureReader.ConnectorMetadata result = StructureReflection.connectorMetadata(
                new Jigsaw(-29, -17), new Info(new OptionalTag(state)));

        assertEquals(state, result.finalState());
        assertEquals(-17, result.selectionPriority());
        assertEquals(-29, result.placementPriority());
    }

    @Test
    public void missingFinalStateRemainsAvailableForConsumerDefaults() throws Exception {
        NativeStructureReader.ConnectorMetadata result = StructureReflection.connectorMetadata(
                new Jigsaw(5, 8), new Info(new OptionalTag(null)));

        assertNull(result.finalState());
        assertEquals(8, result.selectionPriority());
        assertEquals(5, result.placementPriority());
    }

    private record Jigsaw(int placementPriority, int selectionPriority) {
    }

    private record Info(OptionalTag nbt) {
    }

    private record OptionalTag(String state) {
        public Optional<String> getString(String key) {
            return "final_state".equals(key) ? Optional.ofNullable(state) : Optional.empty();
        }
    }
}
