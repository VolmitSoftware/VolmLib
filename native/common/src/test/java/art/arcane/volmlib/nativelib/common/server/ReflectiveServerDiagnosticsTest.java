package art.arcane.volmlib.nativelib.common.server;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReflectiveServerDiagnosticsTest {
    @Test
    public void classifiesWrappedNativeWorldCreationRejection() {
        UnsupportedOperationException rejected = new UnsupportedOperationException("World creation disabled");
        rejected.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "org.bukkit.craftbukkit.CraftServer", "createWorld", "CraftServer.java", 1)});
        assertTrue(new ReflectiveServerDiagnostics().isUnsupportedWorldCreation(new RuntimeException(rejected)));
    }

    @Test
    public void doesNotClassifyOtherNativeOperationsAsWorldCreationRejection() {
        UnsupportedOperationException rejected = new UnsupportedOperationException();
        rejected.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "org.bukkit.craftbukkit.CraftServer", "reload", "CraftServer.java", 1)});
        assertFalse(new ReflectiveServerDiagnostics().isUnsupportedWorldCreation(rejected));
    }

    @Test
    public void distinguishesUnsupportedCreationFromRejectedRuntimeState() {
        IllegalStateException rejected = new IllegalStateException("Cannot create world now");
        rejected.setStackTrace(new StackTraceElement[]{new StackTraceElement(
                "org.bukkit.craftbukkit.CraftServer", "createWorld", "CraftServer.java", 1)});
        ReflectiveServerDiagnostics diagnostics = new ReflectiveServerDiagnostics();
        assertTrue(diagnostics.isRejectedWorldCreation(rejected));
        assertFalse(diagnostics.isUnsupportedWorldCreation(rejected));
    }

    @Test
    public void recognizesOnlyTheRaidPersistenceMessage() {
        ReflectiveServerDiagnostics diagnostics = new ReflectiveServerDiagnostics();
        assertTrue(diagnostics.isRaidPersistenceMessage("Could not save data net.minecraft.world.entity.raid.PersistentRaid"));
        assertFalse(diagnostics.isRaidPersistenceMessage("Could not save data level.dat"));
        assertFalse(diagnostics.isRaidPersistenceMessage(null));
    }
}
