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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class IOWorkerSupport implements Closeable {
    private static final Set<OpenOption> OPTIONS = Set.of(
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE,
            StandardOpenOption.SYNC
    );

    private final Path root;
    private final File tmp;
    private final int maxCacheSize;
    private final AcquireListener acquireListener;
    private final LinkedHashMap<String, Holder> cache = new LinkedHashMap<>(16, 0.75f, true);

    public IOWorkerSupport(File root) {
        this(root, 128, null);
    }

    public IOWorkerSupport(File root, int maxCacheSize, AcquireListener acquireListener) {
        this.root = root.toPath();
        this.tmp = new File(root, ".tmp");
        this.maxCacheSize = Math.max(maxCacheSize, 1);
        this.acquireListener = acquireListener;
    }

    public File createTempFile(String prefix, String suffix) throws IOException {
        tmp.mkdirs();
        return File.createTempFile(prefix, suffix, tmp);
    }

    public <T> T withChannel(String name, ChannelFunction<T> action) throws IOException {
        try (SynchronizedChannel channel = getChannel(name)) {
            return action.apply(channel);
        }
    }

    public void withChannel(String name, ChannelConsumer action) throws IOException {
        withChannel(name, channel -> {
            action.accept(channel);
            return null;
        });
    }

    @Override
    public void close() throws IOException {
        synchronized (cache) {
            for (Holder h : cache.values()) {
                h.close();
            }

            cache.clear();
        }
    }

    private SynchronizedChannel getChannel(String name) throws IOException {
        long startNanos = System.nanoTime();
        try {
            synchronized (cache) {
                Holder holder = cache.get(name);
                if (holder != null) {
                    SynchronizedChannel channel = holder.acquire();
                    if (channel != null) {
                        return channel;
                    }

                    cache.remove(name);
                }

                if (cache.size() >= maxCacheSize) {
                    Iterator<Map.Entry<String, Holder>> iterator = cache.entrySet().iterator();
                    if (iterator.hasNext()) {
                        Map.Entry<String, Holder> eldest = iterator.next();
                        iterator.remove();
                        eldest.getValue().close();
                    }
                }

                holder = new Holder(FileChannel.open(root.resolve(name), OPTIONS));
                cache.put(name, holder);
                SynchronizedChannel channel = holder.acquire();
                if (channel == null) {
                    throw new IOException("Failed to acquire synchronized channel for " + name);
                }

                return channel;
            }
        } finally {
            if (acquireListener != null) {
                acquireListener.onAcquire(name, (System.nanoTime() - startNanos) / 1_000_000L);
            }
        }
    }

    @FunctionalInterface
    public interface ChannelFunction<T> {
        T apply(SynchronizedChannel channel) throws IOException;
    }

    @FunctionalInterface
    public interface ChannelConsumer {
        void accept(SynchronizedChannel channel) throws IOException;
    }

    @FunctionalInterface
    public interface AcquireListener {
        void onAcquire(String name, long millis);
    }
}
