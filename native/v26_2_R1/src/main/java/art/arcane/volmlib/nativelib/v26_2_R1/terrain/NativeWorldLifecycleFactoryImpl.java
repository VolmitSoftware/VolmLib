package art.arcane.volmlib.nativelib.v26_2_R1.terrain;

import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecycleFactory;
import art.arcane.volmlib.nativelib.terrain.NativeWorldLifecyclePolicy;

public final class NativeWorldLifecycleFactoryImpl implements NativeWorldLifecycleFactory {
    @Override
    public Controller create(NativeWorldLifecyclePolicy policy, String generatorClassName) {
        return new NativeWorldLifecycle(policy, generatorClassName);
    }
}
