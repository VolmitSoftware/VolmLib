package art.arcane.volmlib.util.board;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BoardRowSlots {
    private static final int MAX_ROWS = 15;
    private final Map<String, Integer> slots = new HashMap<>(MAX_ROWS);
    private List<String> previous = List.of();
    private int[] previousSlots = new int[0];

    public int[] assign(List<String> identities) {
        if (identities.equals(previous)) {
            return previousSlots.clone();
        }
        if (identities.size() > MAX_ROWS) {
            throw new IllegalArgumentException("A sidebar supports at most 15 row identities");
        }
        Set<String> active = new HashSet<>(identities);
        if (active.size() != identities.size() || active.contains(null)) {
            throw new IllegalArgumentException("Sidebar row identities must be non-null and unique");
        }
        slots.keySet().retainAll(active);
        boolean[] used = new boolean[MAX_ROWS];
        for (int slot : slots.values()) {
            used[slot] = true;
        }
        int[] assigned = new int[identities.size()];
        for (int index = 0; index < identities.size(); index++) {
            String identity = identities.get(index);
            Integer slot = slots.get(identity);
            if (slot == null) {
                for (int candidate = 0; candidate < MAX_ROWS; candidate++) {
                    if (!used[candidate]) {
                        slot = candidate;
                        slots.put(identity, slot);
                        used[slot] = true;
                        break;
                    }
                }
            }
            assigned[index] = slot;
        }
        previous = List.copyOf(identities);
        previousSlots = assigned;
        return assigned.clone();
    }
}
