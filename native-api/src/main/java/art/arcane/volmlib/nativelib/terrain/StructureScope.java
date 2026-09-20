package art.arcane.volmlib.nativelib.terrain;

import java.util.Set;

public interface StructureScope {
    boolean isEmpty();

    boolean isManagedStructureSet(String key);

    boolean allowsStructure(String key, Set<String> sources);

    boolean allowsStructureSet(String key, Set<String> sources);
}
