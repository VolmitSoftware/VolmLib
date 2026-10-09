package art.arcane.volmit.packaging;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeImplementationPackagesTest {
    @Test
    void acceptsExplicitPackageNamesAndMatchesOnlyTheirBoundaries() {
        List<String> packages = NativeImplementationPackages.validate(List.of("com.example.nativeimpl"));
        assertEquals(List.of("com.example.nativeimpl"), packages);
        assertTrue(NativeImplementationPackages.contains("com.example.nativeimpl", packages));
        assertTrue(NativeImplementationPackages.contains("com.example.nativeimpl.v1_21_4", packages));
        assertFalse(NativeImplementationPackages.contains("com.example.nativeimplementation", packages));
        assertFalse(NativeImplementationPackages.contains("com.example.gameplay", packages));
        assertFalse(NativeImplementationPackages.contains("com.example", packages));
    }

    @Test
    void rejectsEmptyRootWildcardAndMalformedPackages() {
        for (String name : List.of("", " ", "com", "com.example", "com.example.*", "com.example.nativeimpl.",
                "com/example/nativeimpl", ".com.example.nativeimpl", "com..nativeimpl", "com.example.class")) {
            assertThrows(GradleException.class, () -> NativeImplementationPackages.validate(List.of(name)), name);
        }
    }
}
