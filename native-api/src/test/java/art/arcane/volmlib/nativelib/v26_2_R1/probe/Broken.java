package art.arcane.volmlib.nativelib.v26_2_R1.probe;

import art.arcane.volmlib.nativelib.NativeAdaptersTest.BrokenProbe;

public final class Broken implements BrokenProbe {
    public Broken() {
        throw new IllegalStateException("broken capability");
    }
}
