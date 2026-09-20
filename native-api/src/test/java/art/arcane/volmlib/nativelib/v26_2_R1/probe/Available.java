package art.arcane.volmlib.nativelib.v26_2_R1.probe;

import art.arcane.volmlib.nativelib.NativeAdaptersTest.Probe;

public final class Available implements Probe {
    @Override
    public String value() {
        return "available";
    }
}
