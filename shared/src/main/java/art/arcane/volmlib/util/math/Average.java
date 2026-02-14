package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.data.DoubleArrayUtils;

public class Average {
    protected final double[] values;
    protected int cursor;
    private double average;
    private double lastSum;
    private boolean dirty;
    private boolean brandNew;

    public Average(int size) {
        values = new double[size];
        DoubleArrayUtils.fill(values, 0);
        brandNew = true;
        average = 0;
        cursor = 0;
        lastSum = 0;
        dirty = false;
    }

    public void put(double value) {
        dirty = true;

        if (brandNew) {
            DoubleArrayUtils.fill(values, value);
            lastSum = size() * value;
            brandNew = false;
            return;
        }

        double current = values[cursor];
        lastSum = (lastSum - current) + value;
        values[cursor] = value;
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
        return values.length;
    }

    public boolean isDirty() {
        return dirty;
    }
}
