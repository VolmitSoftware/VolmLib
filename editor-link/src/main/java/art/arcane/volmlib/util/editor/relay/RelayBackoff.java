package art.arcane.volmlib.util.editor.relay;

/** Reconnect delays: doubling from {@code minimumMillis} to {@code maximumMillis}, each drawn from its upper half. */
public record RelayBackoff(long minimumMillis, long maximumMillis) {
    public RelayBackoff {
        if (minimumMillis < 1 || maximumMillis < minimumMillis) throw new IllegalArgumentException("need 1 <= minimum <= maximum");
    }

    /** The delay before reconnect attempt {@code attempt} (0 first), with {@code unit} in [0, 1] choosing within the half. */
    public long delayMillis(int attempt, double unit) {
        long ceiling = minimumMillis;
        for (int step = 0; step < attempt && ceiling < maximumMillis; step++) ceiling = Math.min(maximumMillis, ceiling * 2);
        long half = ceiling / 2;
        return half + (long) ((ceiling - half) * Math.max(0, Math.min(unit, 1)));
    }
}
