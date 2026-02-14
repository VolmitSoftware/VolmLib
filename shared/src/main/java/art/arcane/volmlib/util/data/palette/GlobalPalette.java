package art.arcane.volmlib.util.data.palette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalPalette<T> implements Palette<T> {
    private final List<T> values;
    private final Map<T, Integer> ids;
    private final T defaultValue;

    @SafeVarargs
    public GlobalPalette(T... initialValues) {
        if (initialValues == null || initialValues.length == 0) {
            throw new IllegalArgumentException("GlobalPalette requires at least one value");
        }

        this.values = new ArrayList<>();
        this.ids = new HashMap<>();
        this.defaultValue = initialValues[0];

        for (T value : initialValues) {
            addIfAbsent(value);
        }
    }

    private void addIfAbsent(T value) {
        if (ids.containsKey(value)) {
            return;
        }

        ids.put(value, values.size());
        values.add(value);
    }

    @Override
    public int idFor(T value) {
        Integer id = ids.get(value);
        return id == null ? 0 : id;
    }

    @Override
    public T valueFor(int id) {
        if (id < 0 || id >= values.size()) {
            return defaultValue;
        }

        T value = values.get(id);
        return value == null ? defaultValue : value;
    }

    @Override
    public int getSize() {
        return values.size();
    }

    @Override
    public void read(List<T> fromList) {
        values.clear();
        ids.clear();

        if (fromList == null || fromList.isEmpty()) {
            addIfAbsent(defaultValue);
            return;
        }

        for (T value : fromList) {
            addIfAbsent(value);
        }
    }

    @Override
    public void write(List<T> toList) {
        toList.addAll(values);
    }
}
