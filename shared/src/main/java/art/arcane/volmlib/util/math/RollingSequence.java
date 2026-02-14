package art.arcane.volmlib.util.math;

import art.arcane.volmlib.util.collection.KList;

public class RollingSequence extends Average {
    private double median;
    private double max;
    private double min;
    private boolean dirtyMedian;
    private int dirtyExtremes;
    private boolean precision;

    public RollingSequence(int size) {
        super(size);
        median = 0;
        min = 0;
        max = 0;
        setPrecision(false);
    }

    public double addLast(int amount) {
        double sum = 0;

        for (int i = 0; i < Math.min(values.length, amount); i++) {
            sum += values[i];
        }

        return sum;
    }

    public boolean isPrecision() {
        return precision;
    }

    public void setPrecision(boolean precision) {
        this.precision = precision;
    }

    public double getMin() {
        if (dirtyExtremes > (isPrecision() ? 0 : values.length)) {
            resetExtremes();
        }

        return min;
    }

    public double getMax() {
        if (dirtyExtremes > (isPrecision() ? 0 : values.length)) {
            resetExtremes();
        }

        return max;
    }

    public double getMedian() {
        if (dirtyMedian) {
            recalculateMedian();
        }

        return median;
    }

    private void recalculateMedian() {
        median = new KList<Double>().forceAdd(values).sort().middleValue();
        dirtyMedian = false;
    }

    public void resetExtremes() {
        max = Integer.MIN_VALUE;
        min = Integer.MAX_VALUE;

        for (double value : values) {
            max = Math.max(max, value);
            min = Math.min(min, value);
        }

        dirtyExtremes = 0;
    }

    @Override
    public void put(double value) {
        super.put(value);
        dirtyMedian = true;
        dirtyExtremes++;
        max = Math.max(max, value);
        min = Math.min(min, value);
    }
}
