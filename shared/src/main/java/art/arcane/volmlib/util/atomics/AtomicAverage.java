package art.arcane.volmlib.util.atomics;

import com.google.common.util.concurrent.AtomicDoubleArray;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Lock-free rolling average over the last {@code size} values. The first value fills the whole window. Each put
 * swaps one slot and adds the difference to a striped sum, so the sum always matches the window's contents and
 * concurrent writers never serialize on a lock.
 */
public class AtomicAverage {
    protected final AtomicDoubleArray values;
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicBoolean brandNew = new AtomicBoolean(true);
    private final DoubleAdder sum = new DoubleAdder();

    public AtomicAverage(int size) {
        values = new AtomicDoubleArray(size);
    }

    public void put(double i) {
        if (brandNew.get() && brandNew.compareAndSet(true, false)) {
            for (int slot = 0; slot < size(); slot++) {
                sum.add(i - values.getAndSet(slot, i));
            }
            return;
        }

        int slot = Math.floorMod(cursor.getAndIncrement(), size());
        sum.add(i - values.getAndSet(slot, i));
    }

    public double getAverage() {
        return sum.sum() / (double) size();
    }

    public int size() {
        return values.length();
    }
}
