package art.arcane.volmlib.util.scheduling;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LooperTest {
    @Test
    public void suppliedNameNamesTheThread() {
        Looper looper = new Looper("Sampler Loop") {
            @Override
            protected long loop() {
                return -1L;
            }
        };

        assertEquals("Sampler Loop", looper.getName());
    }

    @Test
    public void anonymousLooperIsNamedAfterItsOwningClass() {
        Looper looper = new Looper() {
            @Override
            protected long loop() {
                return -1L;
            }
        };

        assertEquals("LooperTest Looper", looper.getName());
    }

    @Test
    public void namedSubclassIsNamedAfterItsNestingChain() {
        assertEquals("LooperTest.PacedLoop Looper", new PacedLoop().getName());
        assertEquals("QueueExecutor Looper", new QueueExecutor().getName());
    }

    @Test
    public void loopersAreDaemonUnlessTheCallerOptsOut() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        Looper looper = new Looper("Opt Out") {
            @Override
            protected long loop() {
                ran.countDown();
                return -1L;
            }
        };
        assertTrue(new PacedLoop().isDaemon());
        assertTrue(looper.isDaemon());

        looper.setDaemon(false);
        looper.start();

        assertTrue(ran.await(5L, TimeUnit.SECONDS));
        looper.join(5_000L);
        assertFalse(looper.isDaemon());
        assertFalse(looper.isAlive());
    }

    private static final class PacedLoop extends Looper {
        @Override
        protected long loop() {
            return -1L;
        }
    }
}
