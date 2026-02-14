package art.arcane.volmlib.util.matter.slices.container;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Position2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JigsawStructuresContainer {
    private final Map<String, List<Position2>> map = new KMap<>();

    public JigsawStructuresContainer() {
    }

    public JigsawStructuresContainer(DataInputStream din) throws IOException {
        int keyCount = din.readInt();
        for (int i = 0; i < keyCount; i++) {
            int positionCount = din.readInt();
            KList<Position2> list = new KList<>(positionCount);
            for (int j = 0; j < positionCount; j++) {
                list.add(new Position2(din.readInt(), din.readInt()));
            }
            map.put(din.readUTF(), list);
        }
    }

    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(map.size());
        for (String key : map.keySet()) {
            List<Position2> list = map.get(key);
            dos.writeInt(list.size());
            for (Position2 pos : list) {
                dos.writeInt(pos.getX());
                dos.writeInt(pos.getZ());
            }
            dos.writeUTF(key);
        }
    }

    public Set<String> getStructures() {
        return Collections.unmodifiableSet(map.keySet());
    }

    public List<Position2> getPositions(String structure) {
        List<Position2> positions = map.get(structure);
        return positions == null ? List.of() : Collections.unmodifiableList(positions);
    }

    public void add(String structureLoadKey, Position2 pos) {
        map.computeIfAbsent(structureLoadKey, k -> new KList<>()).add(pos);
    }
}
