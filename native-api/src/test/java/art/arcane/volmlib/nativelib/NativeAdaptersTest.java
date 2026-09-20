package art.arcane.volmlib.nativelib;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeAdaptersTest {
    @Test
    public void selectsOnlyExplicitSupportedReleases() {
        assertEquals(NativeVersion.V1_21_R7, NativeVersion.resolve("1.21.11-R0.1-SNAPSHOT").orElseThrow());
        assertEquals(NativeVersion.V26_2_R1, NativeVersion.resolve("26.1.2").orElseThrow());
        assertEquals(NativeVersion.V26_2_R1, NativeVersion.resolve("26.1.2.build.74-stable").orElseThrow());
        assertEquals(NativeVersion.V26_2_R1, NativeVersion.resolve("26.2-R0.1-SNAPSHOT").orElseThrow());
        assertEquals(NativeVersion.V26_3_R1, NativeVersion.resolve("26.3.build.25-alpha").orElseThrow());
        for (String version : new String[]{"1.21.10", "26.1", "26.2.1", "27.2", "26.2garbage", "26.2.build.invalid", ""}) {
            assertTrue(version, NativeVersion.resolve(version).isEmpty());
        }
        assertTrue(NativeVersion.resolve(null).isEmpty());
    }

    @Test
    public void acceptsZeroPatchOnlyForAnExplicitlySupportedRelease() {
        assertEquals(NativeVersion.V26_2_R1, NativeVersion.resolve("26.2.0").orElseThrow());
        assertEquals(NativeVersion.V26_3_R1, NativeVersion.resolve("26.3.0-R0.1-SNAPSHOT").orElseThrow());
        assertEquals(NativeVersion.V26_3_R1, NativeVersion.resolve("26.3.0.build.25-alpha").orElseThrow());
        assertTrue(NativeVersion.resolve("26.1.0").isEmpty());
        assertTrue(NativeVersion.resolve("1.21.0").isEmpty());
        assertTrue(NativeVersion.resolve("26.3.1").isEmpty());
    }

    @Test
    public void createsIndependentInstancesOfMatchingCapability() {
        Probe first = NativeAdapters.require(Probe.class, "26.2");
        Probe second = NativeAdapters.require(Probe.class, "26.1.2.build.74-stable");
        assertEquals("available", first.value());
        assertFalse(first == second);
    }

    @Test
    public void missingCapabilitiesStayUnavailableWithoutLoadingAnotherVersion() {
        assertTrue(NativeAdapters.find(Probe.class, "26.3").isEmpty());
        assertTrue(NativeAdapters.find(Probe.class, "27.2").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> NativeAdapters.require(Probe.class, "26.3"));
        assertThrows(IllegalArgumentException.class, () -> NativeAdapters.find(String.class, "26.2"));
    }

    @Test
    public void exposesInitializationFailureInsteadOfTreatingItAsMissing() {
        assertTrue(NativeAdapters.available(BrokenProbe.class, NativeVersion.V26_2_R1));
        assertFalse(NativeAdapters.available(BrokenProbe.class, NativeVersion.V26_3_R1));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> NativeAdapters.find(BrokenProbe.class, "26.2"));
        assertEquals("broken capability", failure.getCause().getMessage());
    }

    @Test
    public void usesRegisteredImplementationLoaderUntilItsOwnerReleasesIt() {
        ClassLoader loader = new ClassLoader(NativeAdapters.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("art.arcane.volmlib.nativelib.v26_2_R1.probe.Available")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        NativeAdapters.registerProviderLoader(loader);
        try {
            assertTrue(NativeAdapters.find(Probe.class, "26.2").isEmpty());
            assertFalse(NativeAdapters.available(Probe.class, NativeVersion.V26_2_R1));
            NativeAdapters.releaseProviderLoader(new ClassLoader() { });
            assertTrue(NativeAdapters.find(Probe.class, "26.2").isEmpty());
        } finally {
            NativeAdapters.releaseProviderLoader(loader);
        }
        assertEquals("available", NativeAdapters.require(Probe.class, "26.2").value());
    }

    @NativeBinding("probe.Available")
    public interface Probe {
        String value();
    }

    @NativeBinding("probe.Broken")
    public interface BrokenProbe {
    }
}
