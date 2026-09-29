package art.arcane.volmlib.util.mantle.io;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class IOWorkerSupportInterruptTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test(timeout = 5_000L)
    public void channelClosedByAnInterruptedReadIsReopenedForTheNextRead() throws Exception {
        File root = temporaryFolder.newFolder("plates");
        byte[] content = {1, 2, 3, 4};
        Files.write(new File(root, "p.0.0.ttp").toPath(), content);
        IOWorkerSupport support = new IOWorkerSupport(root);
        try {
            Thread.currentThread().interrupt();
            try {
                assertThrows(ClosedByInterruptException.class, () -> support.withChannel("p.0.0.ttp",
                        (SynchronizedChannel channel) -> channel.read().readAllBytes()));
            } finally {
                Thread.interrupted();
            }

            byte[] read = support.withChannel("p.0.0.ttp", (SynchronizedChannel channel) -> {
                try (InputStream input = channel.read()) {
                    return input.readAllBytes();
                }
            });

            assertArrayEquals(content, read);
        } finally {
            support.close();
        }
    }
}
