package art.arcane.volmlib.nativelib.common.terrain;

import art.arcane.volmlib.nativelib.terrain.WorldRuntimeExecution;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A level storage access that will not close still holds the world's session lock, and the next attempt to
 * open that world fails with a lock message that names no cause. The close failure used to be swallowed
 * whole, so the console had nothing to connect the two.
 */
public class RuntimeStorageCloseTest {
    private final WorldRuntimeExecution execution = mock(WorldRuntimeExecution.class);

    @Test
    public void aFailedStorageCloseIsReportedWithItsCause() {
        new RuntimeOperations(execution).closeLevelStorageAccess(new FailingStorageAccess());

        ArgumentCaptor<Throwable> reported = ArgumentCaptor.forClass(Throwable.class);
        verify(execution).reportFailure(anyString(), reported.capture());
        assertEquals("session lock still held", reported.getValue().getMessage());
    }

    @Test
    public void aClosableStorageAccessReportsNothing() {
        new RuntimeOperations(execution).closeLevelStorageAccess(new ClosableStorageAccess());

        verify(execution, org.mockito.Mockito.never()).reportFailure(anyString(), org.mockito.ArgumentMatchers.any());
    }

    public static final class FailingStorageAccess {
        public void close() {
            throw new IllegalStateException("session lock still held");
        }
    }

    public static final class ClosableStorageAccess {
        public void close() {
        }
    }
}
