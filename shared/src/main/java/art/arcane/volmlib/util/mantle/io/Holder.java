/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.volmlib.util.mantle.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.Semaphore;

public class Holder {
    private final FileChannel channel;
    private final Semaphore semaphore;
    private volatile boolean closed;

    public Holder(FileChannel channel, Semaphore semaphore) {
        this.channel = channel;
        this.semaphore = semaphore;
    }

    public SynchronizedChannel acquire() {
        semaphore.acquireUninterruptibly();
        // An interrupted read closes the FileChannel under the holder (ClosedByInterruptException); report it
        // closed so the cache reopens the file instead of failing every later read of this plate.
        if (closed || !channel.isOpen()) {
            closed = true;
            semaphore.release();
            return null;
        }

        return new SynchronizedChannel(channel, semaphore);
    }

    public void close() throws IOException {
        semaphore.acquireUninterruptibly();
        try {
            if (closed) {
                return;
            }
            closed = true;
            channel.close();
        } finally {
            semaphore.release();
        }
    }
}
