package art.arcane.volmlib.nativelib.monitor;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs a self-attach once and keeps the outcome, failure included, so a JVM that refuses
 * dynamic attach does not block every later caller for seconds.
 */
public final class InstrumentationAttach {
    /**
     * Plugins shade and relocate their own copy of this class, so a refusal is also recorded in a
     * System property that every copy, and a hotloaded copy, can see.
     */
    public static final String FAILED_PROPERTY = "volmlib.instrumentation.attach.failed";

    private final Callable<Instrumentation> installer;
    private final String failedProperty;
    private final Logger log;
    private volatile boolean attempted;
    private volatile Instrumentation instrumentation;

    public InstrumentationAttach(Callable<Instrumentation> installer, Logger log) {
        this(installer, FAILED_PROPERTY, log);
    }

    InstrumentationAttach(Callable<Instrumentation> installer, String failedProperty, Logger log) {
        this.installer = installer;
        this.failedProperty = failedProperty;
        this.log = log;
    }

    /** Returns the instrumentation, or null when attach is unavailable. Only the first call can block. */
    public Instrumentation get() {
        if (attempted) {
            return instrumentation;
        }
        // The interned property name is one object across classloaders, so relocated copies
        // in different plugins wait on the same lock instead of attaching in parallel.
        synchronized (failedProperty.intern()) {
            if (attempted) {
                return instrumentation;
            }
            String earlierFailure = System.getProperty(failedProperty);
            if (earlierFailure == null) {
                Instrumentation result = null;
                String failure = "installer returned no instrumentation";
                try {
                    result = installer.call();
                } catch (Throwable t) {
                    failure = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
                    log.log(Level.WARNING, "[VolmLib] Bytecode attach failed, not retrying: " + failure, t);
                }
                if (result == null) {
                    System.setProperty(failedProperty, failure);
                }
                instrumentation = result;
            }
            attempted = true;
            return instrumentation;
        }
    }
}
