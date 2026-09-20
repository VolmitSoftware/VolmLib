package art.arcane.volmlib.nativelib.common.structure;

import art.arcane.volmlib.nativelib.terrain.NativeStructureReader;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StructureElementReadTest {
    @Test
    public void exposesOrderedCompositeChildrenWithoutChoosingAnImportPolicy() throws Exception {
        FakeListPoolElement list = new FakeListPoolElement(List.of(
                new SinglePoolElement(new Identifier("example", "first")),
                new SinglePoolElement(new Identifier("example", "overlay"))));
        NativeStructureReader.Element element = new ReflectiveStructureReader.ElementView(list, null, ReflectiveStructureReader::readDefaultConnectors);

        List<NativeStructureReader.Element> children = element.children();

        assertEquals(2, children.size());
        assertEquals("example:first", children.getFirst().templateLocation());
        assertEquals("example:overlay", children.get(1).templateLocation());
        assertNull(children.getFirst().children());
    }

    @Test
    public void missingConnectorCapabilityDoesNotConsumeTheRandomSeed() throws Exception {
        NativeStructureReader.Element element = new ReflectiveStructureReader.ElementView(
                new SinglePoolElement(new Identifier("example", "first")), null, ReflectiveStructureReader::readDefaultConnectors);

        NativeStructureReader.ConnectorSet connectors = element.connectors(() -> {
            throw new AssertionError("Unsupported connector extraction consumed randomness");
        });

        assertEquals(NativeStructureReader.ConnectorStatus.UNSUPPORTED, connectors.status());
        assertEquals(List.of(), connectors.connectors());
    }

    private record FakeListPoolElement(List<?> elements) {
        public List<?> getElements() { return elements; }
    }

    private record SinglePoolElement(Identifier location) {
        public Identifier getTemplateLocation() { return location; }
    }

    private record Identifier(String namespace, String path) {
        public String getNamespace() { return namespace; }
        public String getPath() { return path; }
    }
}
