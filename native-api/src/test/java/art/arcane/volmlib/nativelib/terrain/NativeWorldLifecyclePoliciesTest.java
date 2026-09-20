package art.arcane.volmlib.nativelib.terrain;

import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class NativeWorldLifecyclePoliciesTest {
    @Test
    public void bridgeLookupUsesSharedPolicyAndOldOwnerCannotRemoveReplacement() throws Exception {
        NativeWorldLifecyclePolicy original = policy();
        NativeWorldLifecyclePolicy replacement = policy();
        NativeWorldLifecyclePolicies.register(original);
        try {
            Class<?> bridge = Class.forName(NativeWorldLifecyclePolicies.class.getName(), true,
                    NativeWorldLifecyclePolicy.class.getClassLoader());
            assertSame(original, bridge.getMethod("policy", String.class).invoke(null, "test-lifecycle"));
            NativeWorldLifecyclePolicies.register(replacement);
            NativeWorldLifecyclePolicies.unregister(original);
            assertSame(replacement, NativeWorldLifecyclePolicies.policy("test-lifecycle"));
        } finally {
            NativeWorldLifecyclePolicies.unregister(replacement);
            NativeWorldLifecyclePolicies.unregister(original);
        }
        assertNull(NativeWorldLifecyclePolicies.policy("test-lifecycle"));
    }

    private static NativeWorldLifecyclePolicy policy() {
        return (NativeWorldLifecyclePolicy) Proxy.newProxyInstance(NativeWorldLifecyclePolicy.class.getClassLoader(),
                new Class<?>[]{NativeWorldLifecyclePolicy.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "pluginName" -> "test-lifecycle";
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                });
    }
}
