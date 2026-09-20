package art.arcane.volmlib.nativelib.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ClientWorldPolicies {
    private static final List<ClientWorldPolicy> POLICIES = new CopyOnWriteArrayList<>();

    private ClientWorldPolicies() {
    }

    public static void register(ClientWorldPolicy policy) {
        POLICIES.removeIf(existing -> existing.namespace().equals(policy.namespace()));
        POLICIES.add(policy);
    }

    public static ClientWorldPolicy forNamespace(String namespace) {
        for (ClientWorldPolicy policy : POLICIES) {
            if (policy.namespace().equals(namespace)) {
                return policy;
            }
        }
        return null;
    }

    public static boolean skipsCompatibilityWarning(Class<?> generatorType) {
        for (ClientWorldPolicy policy : POLICIES) {
            if (policy.skipExperimentalWarning() && policy.generatorType().isAssignableFrom(generatorType)) {
                return true;
            }
        }
        return false;
    }
}
