package art.arcane.volmlib.nativelib.terrain;

import art.arcane.volmlib.nativelib.NativeBinding;

import java.util.List;
import java.util.function.LongSupplier;

@NativeBinding("terrain.NativeStructureReaderImpl")
public interface NativeStructureReader {
    List<String> templates() throws Exception;
    Session open() throws Exception;

    interface Session {
        Structure structure(String key) throws Exception;
        Pool pool(String key) throws Exception;
    }

    interface Structure {
        String typeName();
        boolean jigsaw();
        String startPoolKey() throws Exception;
        int maxDepth() throws Exception;
        int maxDistanceFromCenter() throws Exception;
    }

    interface Pool {
        String fallbackKey() throws Exception;
        List<Entry> entries() throws Exception;
    }

    interface Entry {
        Element element() throws Exception;
        int weight() throws Exception;
    }

    interface Element {
        String typeName();
        String templateLocation() throws Exception;
        List<Element> children() throws Exception;
        ConnectorSet connectors(LongSupplier randomSeed) throws Exception;
    }

    interface Connector {
        ConnectorData read() throws Exception;
    }

    enum ConnectorStatus {
        AVAILABLE,
        UNSUPPORTED,
        MISSING
    }

    record ConnectorSet(ConnectorStatus status, List<Connector> connectors) {
    }

    record ConnectorMetadata(String finalState, int selectionPriority, int placementPriority) {
    }

    record ConnectorData(int x, int y, int z, String front, String top, String pool,
                         String name, String target, String joint, ConnectorMetadata metadata) {
    }
}
