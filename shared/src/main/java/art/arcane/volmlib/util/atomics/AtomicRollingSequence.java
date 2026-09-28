package art.arcane.volmlib.util.atomics;

import art.arcane.volmlib.util.collection.KList;

/**
 * Rolling window of the last {@code size} values. Extremes and the median are read from the window on demand, so
 * a put costs no more than the underlying {@link AtomicAverage}.
 */
public class AtomicRollingSequence extends AtomicAverage {
    public AtomicRollingSequence(int size) {
        super(size);
    }

    public double addLast(int amt) {
        double f = 0;

        for (int i = 0; i < Math.min(values.length(), amt); i++) {
            f += values.get(i);
        }

        return f;
    }

    public double getMin() {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < values.length(); i++) {
            min = Math.min(min, values.get(i));
        }
        return min;
    }

    public double getMax() {
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < values.length(); i++) {
            max = Math.max(max, values.get(i));
        }
        return max;
    }

    public double getMedian() {
        return new KList<Double>().forceAdd(values).sort().middleValue();
    }
}
