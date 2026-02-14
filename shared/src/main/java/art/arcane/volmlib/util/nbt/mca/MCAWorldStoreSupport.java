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

package art.arcane.volmlib.util.nbt.mca;

import art.arcane.volmlib.util.collection.KMap;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class MCAWorldStoreSupport<M> {
    @FunctionalInterface
    public interface GridLock {
        <T> T withResult(int x, int z, Supplier<T> supplier);
    }

    public interface KeyCodec {
        long key(int x, int z);

        int keyX(long key);

        int keyZ(long key);
    }

    @FunctionalInterface
    public interface RegionFactory<M> {
        M create(int x, int z);
    }

    @FunctionalInterface
    public interface RegionFileResolver {
        File resolve(int x, int z);
    }

    @FunctionalInterface
    public interface RegionWriter<M> {
        void write(M region, File file, boolean changeLastUpdate) throws IOException;
    }

    public interface Logger {
        void info(String message);

        void debug(String message);

        void error(String message, Throwable error);
    }

    public static class Config<M> {
        public final KeyCodec keyCodec;
        public final GridLock lock;
        public final RegionFactory<M> regionFactory;
        public final RegionFileResolver fileResolver;
        public final RegionWriter<M> regionWriter;
        public final Logger logger;
        public final LongSupplier nowMs;
        public final long unloadAfterMs;
        public final String writerThreadName;

        public Config(
                KeyCodec keyCodec,
                GridLock lock,
                RegionFactory<M> regionFactory,
                RegionFileResolver fileResolver,
                RegionWriter<M> regionWriter,
                Logger logger,
                LongSupplier nowMs,
                long unloadAfterMs,
                String writerThreadName
        ) {
            this.keyCodec = keyCodec;
            this.lock = lock;
            this.regionFactory = regionFactory;
            this.fileResolver = fileResolver;
            this.regionWriter = regionWriter;
            this.logger = logger;
            this.nowMs = nowMs;
            this.unloadAfterMs = unloadAfterMs;
            this.writerThreadName = writerThreadName;
        }
    }

    private final Config<M> config;
    private final KMap<Long, M> loadedRegions;
    private final KMap<Long, Long> lastUse;
    private final ExecutorService saveQueue;

    public MCAWorldStoreSupport(Config<M> config) {
        this.config = config;
        this.loadedRegions = new KMap<>();
        this.lastUse = new KMap<>();
        this.saveQueue = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName(config.writerThreadName);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    public void close() {
        for (Long key : loadedRegions.k()) {
            queueSaveUnload(config.keyCodec.keyX(key), config.keyCodec.keyZ(key));
        }

        saveQueue.shutdown();
        try {
            while (!saveQueue.awaitTermination(3, TimeUnit.SECONDS)) {
                config.logger.info("Still Waiting to save MCA Files...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            config.logger.error("Interrupted while waiting for MCA writer shutdown", e);
        }
    }

    public void flushNow() {
        for (Long key : loadedRegions.k()) {
            doSaveUnload(config.keyCodec.keyX(key), config.keyCodec.keyZ(key));
        }
    }

    public void queueSaveUnload(int x, int z) {
        saveQueue.submit(() -> doSaveUnload(x, z));
    }

    public void doSaveUnload(int x, int z) {
        M region = getMCAOrNull(x, z);
        if (region != null) {
            unloadRegion(x, z);
        }
        saveRegion(x, z, region);
    }

    public void save() {
        for (Long key : loadedRegions.k()) {
            int x = config.keyCodec.keyX(key);
            int z = config.keyCodec.keyZ(key);

            if (!lastUse.containsKey(key)) {
                lastUse.put(key, config.nowMs.getAsLong());
            }

            if (shouldUnload(x, z)) {
                queueSaveUnload(x, z);
            }
        }

        config.logger.debug("Regions: " + loadedRegions.size());
    }

    public synchronized void unloadRegion(int x, int z) {
        long key = config.keyCodec.key(x, z);
        loadedRegions.remove(key);
        lastUse.remove(key);
        config.logger.debug("Unloaded Region " + x + " " + z);
    }

    public void saveRegion(int x, int z) {
        M region = getMCAOrNull(x, z);
        saveRegion(x, z, region);
    }

    public void saveRegion(int x, int z, M region) {
        try {
            config.regionWriter.write(region, getRegionFile(x, z), true);
            config.logger.debug("Saved Region " + x + " " + z);
        } catch (IOException e) {
            config.logger.error("Failed to save region " + getRegionFile(x, z).getPath(), e);
        }
    }

    public boolean shouldUnload(int x, int z) {
        return getIdleDuration(x, z) > config.unloadAfterMs;
    }

    public File getRegionFile(int x, int z) {
        return config.fileResolver.resolve(x, z);
    }

    public long getIdleDuration(int x, int z) {
        return config.lock.withResult(x, z, () -> {
            Long last = lastUse.get(config.keyCodec.key(x, z));
            return last == null ? 0L : config.nowMs.getAsLong() - last;
        });
    }

    public M getMCA(int x, int z) {
        long key = config.keyCodec.key(x, z);
        return config.lock.withResult(x, z, () -> {
            lastUse.put(key, config.nowMs.getAsLong());
            M region = loadedRegions.get(key);
            if (region == null) {
                region = config.regionFactory.create(x, z);
                loadedRegions.put(key, region);
            }
            return region;
        });
    }

    public M getMCAOrNull(int x, int z) {
        long key = config.keyCodec.key(x, z);
        return config.lock.withResult(x, z, () -> {
            if (!loadedRegions.containsKey(key)) {
                return null;
            }
            lastUse.put(key, config.nowMs.getAsLong());
            return loadedRegions.get(key);
        });
    }

    public int size() {
        return loadedRegions.size();
    }

    public boolean isLoaded(int x, int z) {
        return loadedRegions.containsKey(config.keyCodec.key(x, z));
    }
}
