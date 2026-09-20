package art.arcane.volmlib.nativelib.terrain;

import java.util.Objects;
import art.arcane.volmlib.nativelib.NativeBinding;

@NativeBinding("terrain.NativeGenerationRegistryImpl")
public interface NativeGenerationRegistry {
    Definition definition(String registryKey, String resourceKey);

    default Definition generatedDefinition(String registryKey, String resourceKey) {
        return definition(registryKey, resourceKey);
    }

    default Definition canonicalDefinition(String registryKey, String resourceKey, String sourceJson) {
        return Definition.exactJson(sourceJson);
    }

    enum Encoding {
        JSON,
        CONSERVATIVE_IDENTITY
    }

    record Definition(Encoding encoding, String value) {
        public Definition {
            encoding = Objects.requireNonNull(encoding, "encoding");
            value = Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("Platform registry definition must not be blank.");
            }
        }

        public static Definition exactJson(String json) {
            return new Definition(Encoding.JSON, json);
        }

        public static Definition conservativeIdentity(String identity) {
            return new Definition(Encoding.CONSERVATIVE_IDENTITY, identity);
        }

        public static Definition resourceIdentity(String registryKey, String resourceKey) {
            String requiredRegistryKey = requireText(registryKey, "registryKey");
            String requiredResourceKey = requireText(resourceKey, "resourceKey");
            return conservativeIdentity(
                    "registry-resource-key-v1|" + requiredRegistryKey + "|" + requiredResourceKey
            );
        }

        private static String requireText(String value, String label) {
            String required = Objects.requireNonNull(value, label).trim();
            if (required.isEmpty()) {
                throw new IllegalArgumentException(label + " must not be blank.");
            }
            return required;
        }
    }
}
