package art.arcane.volmlib.nativelib.terrain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class NativeWorldLifecyclePolicies {
    private static final ConcurrentMap<String, NativeWorldLifecyclePolicy> POLICIES = new ConcurrentHashMap<>();

    private NativeWorldLifecyclePolicies() {
    }

    public static void register(NativeWorldLifecyclePolicy policy) {
        POLICIES.put(policy.pluginName(), policy);
    }

    public static void unregister(NativeWorldLifecyclePolicy policy) {
        POLICIES.remove(policy.pluginName(), policy);
    }

    public static NativeWorldLifecyclePolicy policy(String pluginName) {
        return POLICIES.get(pluginName);
    }
}
