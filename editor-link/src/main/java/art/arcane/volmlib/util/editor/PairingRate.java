package art.arcane.volmlib.util.editor;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * The per-address pairing rate: a fixed one-minute window per address, IPv6 counted by its {@code /64}, shared by pairing
 * attempts and failed event-stream authentications. Past {@link #ADDRESSES} addresses in one minute, new pairing attempts
 * share a single window so the map stays bounded; event streams never do, so a pairing flood cannot lock them out.
 */
public final class PairingRate {
    public static final int ADDRESSES = 4096;
    private static final String SHARED = "";
    private final Clock clock;
    private final IntSupplier perMinute;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    /** {@code perMinute} is read on every attempt, so a changed setting applies at once. */
    public PairingRate(Clock clock, IntSupplier perMinute) {
        this.clock = Objects.requireNonNull(clock);
        this.perMinute = Objects.requireNonNull(perMinute);
    }

    /** Counts a pairing attempt and refuses it past the limit. */
    public void pairing(String address) throws EditorRefusal {
        long[] window = window(address, true);
        synchronized (window) {
            if (++window[1] > perMinute.getAsInt()) throw limited();
        }
    }

    /**
     * Refuses an event stream from an address whose window is used up, before its token is checked, so a guesser gets no
     * more tries this minute. Only {@link #failed} counts; reconnects with a valid token do not.
     */
    public void guard(String address) throws EditorRefusal {
        long[] window = window(address, false);
        if (window == null) return;
        synchronized (window) {
            if (window[1] >= perMinute.getAsInt()) throw limited();
        }
    }

    /** Counts an event stream whose token was refused. */
    public void failed(String address) {
        long[] window = window(address, false);
        if (window == null) return;
        synchronized (window) { window[1]++; }
    }

    private long[] window(String address, boolean shared) {
        String key = EditorAddresses.key(address);
        long minute = clock.millis() / 60_000;
        long[] window = windows.get(key);
        if (window == null) {
            if (windows.size() >= ADDRESSES) windows.entrySet().removeIf((Map.Entry<String, long[]> entry) -> entry.getValue()[0] != minute);
            if (windows.size() >= ADDRESSES) {
                if (!shared) return null;
                key = SHARED;
            }
            window = windows.computeIfAbsent(key, (String unused) -> new long[]{minute, 0});
        }
        synchronized (window) {
            if (window[0] != minute) {
                window[0] = minute;
                window[1] = 0;
            }
        }
        return window;
    }

    /** Attempts count per clock minute, so the wait is what is left of this one. */
    private EditorRefusal limited() {
        long wait = 60_000 - clock.millis() % 60_000;
        return new EditorRefusal(EditorRefusal.Code.RATE_LIMITED, "Too many pairing attempts from this address. Try again in " + (wait + 999) / 1000 + " s.", wait, null);
    }
}
