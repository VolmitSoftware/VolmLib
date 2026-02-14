package art.arcane.volmlib.util.atomics;

import com.google.common.util.concurrent.AtomicDoubleArray;
import art.arcane.volmlib.util.data.DoubleArrayUtils;

/**
 * Fast rolling average using an atomic ring buffer.
 */
public class AtomicAverage {
    protected final AtomicDoubleArray values;
    protected transient int cursor;
    private transient double average;
    private transient double lastSum;
    private transient boolean dirty;
    private transient boolean brandNew;

    public AtomicAverage(int size) {
        values = new AtomicDoubleArray(size);
        DoubleArrayUtils.fill(values, 0);
        brandNew = true;
        average = 0;
        cursor = 0;
        lastSum = 0;
        dirty = false;
    }

    public synchronized void put(double i) {
        dirty = true;

        if (brandNew) {
            DoubleArrayUtils.fill(values, i);
            lastSum = size() * i;
            brandNew = false;
            return;
        }

        double current = values.get(cursor);
        lastSum = (lastSum - current) + i;
        values.set(cursor, i);
        cursor = cursor + 1 < size() ? cursor + 1 : 0;
    }

    public double getAverage() {
        if (dirty) {
            calculateAverage();
            return getAverage();
        }

        return average;
    }

    private void calculateAverage() {
        average = lastSum / (double) size();
        dirty = false;
    }

    public int size() {
        return values.length();
    }

    public boolean isDirty() {
        return dirty;
    }
}
