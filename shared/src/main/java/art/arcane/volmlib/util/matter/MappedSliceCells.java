package art.arcane.volmlib.util.matter;

import art.arcane.volmlib.util.hunk.storage.MappedHunk;

import java.util.Arrays;
import java.util.Map;

final class MappedSliceCells {
    private final int height;
    private final int depth;
    private final long[] order;
    private final Object[] values;

    private MappedSliceCells(int height, int depth, long[] order, Object[] values) {
        this.height = height;
        this.depth = depth;
        this.order = order;
        this.values = values;
    }

    static MappedSliceCells of(MappedHunk<?> mapped) {
        int width = mapped.getWidth();
        int height = mapped.getHeight();
        int depth = mapped.getDepth();
        int plane = width * height;
        Map<Integer, ?> data = mapped.getData();
        long[] order = new long[data.size()];
        Object[] entries = new Object[order.length];
        int count = 0;
        for (Map.Entry<Integer, ?> entry : data.entrySet()) {
            if (count == order.length) {
                order = Arrays.copyOf(order, Math.max(8, count * 2));
                entries = Arrays.copyOf(entries, order.length);
            }
            int index = entry.getKey();
            int z = index / plane;
            int remainder = index - z * plane;
            int y = remainder / width;
            int x = remainder - y * width;
            order[count] = ((long) ((x * height + y) * depth + z) << 32) | count;
            entries[count] = entry.getValue();
            count++;
        }
        Arrays.sort(order, 0, count);
        long[] cells = new long[count];
        Object[] values = new Object[count];
        for (int index = 0; index < count; index++) {
            cells[index] = order[index] >>> 32;
            values[index] = entries[(int) order[index]];
        }
        return new MappedSliceCells(height, depth, cells, values);
    }

    int size() {
        return order.length;
    }

    int x(int index) {
        return (int) (order[index] / ((long) height * depth));
    }

    int y(int index) {
        return (int) ((order[index] / depth) % height);
    }

    int z(int index) {
        return (int) (order[index] % depth);
    }

    Object value(int index) {
        return values[index];
    }
}
