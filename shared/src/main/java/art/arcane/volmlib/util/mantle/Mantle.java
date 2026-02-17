package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.documentation.RegionCoordinates;
import art.arcane.volmlib.util.function.Consumer3;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.parallel.HyperLockSupport;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import org.bukkit.Chunk;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared concrete mantle runtime with plugin hook points for chunk data behavior,
 * persistence, and logging/reporting.
 */
public abstract class Mantle<P extends TectonicPlate<C>, C extends MantleChunk<?>> extends MantleAccessSupport<P> {
    public static final int DEFAULT_LOCK_SIZE = Short.MAX_VALUE;

    private final File dataFolder;
    private final int worldHeight;
    private final KMap<Long, Long> lastUse;
    private final KMap<Long, P> loadedRegions;
    private final HyperLockSupport hyperLock;
    private final AtomicBoolean closed;
    private final MultiBurstSupport ioBurst;
    private final RegionIO<P> regionIO;
    private final KSet<Long> toUnload;

    private volatile double adjustedIdleDuration;

    protected Mantle(File dataFolder,
                     int worldHeight,
                     HyperLockSupport hyperLock,
                     MultiBurstSupport ioBurst,
                     RegionIO<P> regionIO) {
        this(dataFolder, worldHeight, DEFAULT_LOCK_SIZE, hyperLock, ioBurst, regionIO);
    }

    protected Mantle(File dataFolder,
                     int worldHeight,
                     int lockSize,
                     HyperLockSupport hyperLock,
                     MultiBurstSupport ioBurst,
                     RegionIO<P> regionIO) {
        super(lockSize);
        this.dataFolder = dataFolder;
        this.worldHeight = worldHeight;
        this.hyperLock = hyperLock;
        this.ioBurst = ioBurst;
        this.regionIO = regionIO;
        this.loadedRegions = new KMap<>();
        this.lastUse = new KMap<>();
        this.toUnload = new KSet<>();
        this.closed = new AtomicBoolean(false);
        this.adjustedIdleDuration = 0;

        onDebug("Opened The Mantle " + dataFolder.getAbsolutePath());
    }

    public static File fileForRegion(File folder, int x, int z) {
        return MantleRegionFiles.fileForRegion(folder, x, z);
    }

    public static File fileForRegion(File folder, Long key, boolean convert) {
        return MantleRegionFiles.fileForRegion(folder, key, convert);
    }

    public static File oldFileForRegion(File folder, Long key) {
        return MantleRegionFiles.oldFileForRegion(folder, key);
    }

    public static Long key(int x, int z) {
        return MantleRegionFiles.key(x, z);
    }

    public File getDataFolder() {
        return dataFolder;
    }

    public int getWorldHeight() {
        return worldHeight;
    }

    public void clear() {
        loadedRegions.values().forEach(TectonicPlate::clear);
        loadedRegions.clear();
        lastUse.clear();
        toUnload.clear();
    }

    @ChunkCoordinates
    public void raiseFlag(int x, int z, MantleFlag flag, Runnable r) {
        if (!hasFlag(x, z, flag)) {
            flag(x, z, flag, true);
            r.run();
        }
    }

    @ChunkCoordinates
    public void lowerFlag(int x, int z, MantleFlag flag, Runnable r) {
        if (hasFlag(x, z, flag)) {
            flag(x, z, flag, false);
            r.run();
        }
    }

    @ChunkCoordinates
    public C getChunk(int x, int z) {
        return get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31);
    }

    public C getChunk(Chunk chunk) {
        return getChunk(chunk.getX(), chunk.getZ());
    }

    public void getChunks(final int minChunkX,
                          final int maxChunkX,
                          final int minChunkZ,
                          final int maxChunkZ,
                          int parallelism,
                          final Consumer3<Integer, Integer, C> consumer) {
        if (parallelism <= 0) {
            parallelism = 1;
        }

        final int minRegionX = minChunkX >> 5;
        final int maxRegionX = maxChunkX >> 5;
        final int minRegionZ = minChunkZ >> 5;
        final int maxRegionZ = maxChunkZ >> 5;

        final int minRelativeX = minChunkX & 31;
        final int maxRelativeX = maxChunkX & 31;
        final int minRelativeZ = minChunkZ & 31;
        final int maxRelativeZ = maxChunkZ & 31;

        if (parallelism <= 1) {
            for (int rX = minRegionX; rX <= maxRegionX; rX++) {
                final int minX = rX == minRegionX ? minRelativeX : 0;
                final int maxX = rX == maxRegionX ? maxRelativeX : 31;
                for (int rZ = minRegionZ; rZ <= maxRegionZ; rZ++) {
                    final int minZ = rZ == minRegionZ ? minRelativeZ : 0;
                    final int maxZ = rZ == maxRegionZ ? maxRelativeZ : 31;
                    final int realX = rX << 5;
                    final int realZ = rZ << 5;

                    P region = get(rX, rZ);
                    C zero = region.getOrCreate(0, 0);
                    zero.use();
                    try {
                        for (int xx = minX; xx <= maxX; xx++) {
                            for (int zz = minZ; zz <= maxZ; zz++) {
                                consumer.accept(realX + xx, realZ + zz, region.getOrCreate(xx, zz));
                            }
                        }
                    } finally {
                        zero.release();
                    }
                }
            }
            return;
        }

        final Semaphore lock = new Semaphore(parallelism);
        final AtomicInteger queued = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicLong waitStart = new AtomicLong(nowMillis());

        final AtomicReference<Throwable> error = new AtomicReference<>();
        for (int rX = minRegionX; rX <= maxRegionX; rX++) {
            final int minX = rX == minRegionX ? minRelativeX : 0;
            final int maxX = rX == maxRegionX ? maxRelativeX : 31;
            for (int rZ = minRegionZ; rZ <= maxRegionZ; rZ++) {
                final int minZ = rZ == minRegionZ ? minRelativeZ : 0;
                final int maxZ = rZ == maxRegionZ ? maxRelativeZ : 31;
                final int realX = rX << 5;
                final int realZ = rZ << 5;

                while (true) {
                    try {
                        if (lock.tryAcquire(5, TimeUnit.SECONDS)) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        onWarn("Mantle.getChunks interrupted while waiting for permit.");
                        onError(e);
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting for Mantle.getChunks permit", e);
                    }

                    onWarn("Mantle.getChunks permit wait " + (nowMillis() - waitStart.get()) + "ms"
                            + " queued=" + queued.get()
                            + " completed=" + completed.get()
                            + " parallelism=" + parallelism
                            + " range=" + minChunkX + "," + minChunkZ + "->" + maxChunkX + "," + maxChunkZ);
                }
                Throwable failure = error.get();
                if (failure != null) {
                    if (failure instanceof RuntimeException re) {
                        throw re;
                    }
                    if (failure instanceof Error err) {
                        throw err;
                    }
                    throw new RuntimeException(failure);
                }

                queued.incrementAndGet();
                getFuture(rX, rZ)
                        .thenAccept(region -> {
                            C zero = region.getOrCreate(0, 0);
                            zero.use();
                            try {
                                for (int xx = minX; xx <= maxX; xx++) {
                                    for (int zz = minZ; zz <= maxZ; zz++) {
                                        consumer.accept(realX + xx, realZ + zz, region.getOrCreate(xx, zz));
                                    }
                                }
                            } finally {
                                zero.release();
                            }
                        })
                        .exceptionally(ex -> {
                            error.set(ex);
                            return null;
                        })
                        .thenRun(() -> {
                            completed.incrementAndGet();
                            lock.release();
                        });
            }
        }

        while (true) {
            try {
                if (lock.tryAcquire(parallelism, 5, TimeUnit.SECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                onWarn("Mantle.getChunks interrupted while waiting for completion.");
                onError(e);
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Mantle.getChunks completion", e);
            }

            onWarn("Mantle.getChunks completion wait " + (nowMillis() - waitStart.get()) + "ms"
                    + " queued=" + queued.get()
                    + " completed=" + completed.get()
                    + " parallelism=" + parallelism
                    + " range=" + minChunkX + "," + minChunkZ + "->" + maxChunkX + "," + maxChunkZ);
        }
    }

    @ChunkCoordinates
    public void flag(int x, int z, MantleFlag flag, boolean flagged) {
        get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).flag(flag, flagged);
    }

    public void deleteChunk(int x, int z) {
        long started = nowMillis();
        boolean trace = Thread.currentThread().getName().startsWith("Iris-Regen");
        if (trace) {
            onWarn("Mantle deleteChunk start: chunk=" + x + "," + z
                    + " thread=" + Thread.currentThread().getName());
        }
        get(x >> 5, z >> 5).delete(x & 31, z & 31);
        if (trace) {
            long took = nowMillis() - started;
            onWarn("Mantle deleteChunk done: chunk=" + x + "," + z
                    + " tookMs=" + took
                    + " thread=" + Thread.currentThread().getName());
        }
    }

    @RegionCoordinates
    public boolean hasTectonicPlate(int x, int z) {
        Long k = key(x, z);
        return loadedRegions.containsKey(k) || fileForRegion(dataFolder, k, true).exists();
    }

    @ChunkCoordinates
    public <T> void iterateChunk(int x, int z, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
        iterateChunkValues(get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31), type, iterator);
    }

    @ChunkCoordinates
    public boolean hasFlag(int x, int z, MantleFlag flag) {
        if (!hasTectonicPlate(x >> 5, z >> 5)) {
            return false;
        }

        return get(x >> 5, z >> 5).getOrCreate(x & 31, z & 31).isFlagged(flag);
    }

    @BlockCoordinates
    public <T> void set(int x, int y, int z, T t) {
        ensureOpen();
        if (y < 0 || y >= worldHeight) {
            return;
        }

        C chunk = get((x >> 4) >> 5, (z >> 4) >> 5).getOrCreate((x >> 4) & 31, (z >> 4) & 31);
        setChunkValue(chunk, x & 15, y, z & 15, t);
    }

    @BlockCoordinates
    public <T> void remove(int x, int y, int z, Class<T> type) {
        ensureOpen();
        if (y < 0 || y >= worldHeight) {
            return;
        }

        C chunk = get((x >> 4) >> 5, (z >> 4) >> 5).getOrCreate((x >> 4) & 31, (z >> 4) & 31);
        removeChunkValue(chunk, x & 15, y, z & 15, type);
    }

    @BlockCoordinates
    public <T> T get(int x, int y, int z, Class<T> type) {
        ensureOpen();

        if (!hasTectonicPlate((x >> 4) >> 5, (z >> 4) >> 5)) {
            return null;
        }

        if (y < 0 || y >= worldHeight) {
            return null;
        }

        C chunk = get((x >> 4) >> 5, (z >> 4) >> 5).getOrCreate((x >> 4) & 31, (z >> 4) & 31);
        return getChunkValue(chunk, x & 15, y, z & 15, type);
    }

    public boolean isClosed() {
        return closed.get();
    }

    public synchronized void close() {
        onDebug("Closing The Mantle " + dataFolder.getAbsolutePath());
        if (closed.getAndSet(true)) {
            return;
        }

        hyperLock.disable();
        flushLoadedRegions();
        loadedRegions.clear();
        lastUse.clear();
        toUnload.clear();

        try {
            regionIO.close();
        } catch (Throwable e) {
            onError(e);
        }

        deleteTemporaryFiles();
        onDebug("The Mantle has Closed " + dataFolder.getAbsolutePath());
    }

    public synchronized void trim(long baseIdleDuration) {
        trim(baseIdleDuration, Integer.MAX_VALUE);
    }

    public synchronized void trim(long baseIdleDuration, int tectonicLimit) {
        ensureOpen();

        adjustedIdleDuration = MantleLifecycleSupport.adjustIdleDuration(baseIdleDuration, loadedRegions.size(), tectonicLimit);

        trimSemaphore().acquireUninterruptibly(DEFAULT_LOCK_SIZE);
        try {
            onDebug("Trimming Tectonic Plates older than " + formatDuration(adjustedIdleDuration));
            if (lastUse.isEmpty()) {
                return;
            }

            MantleLifecycleSupport.collectUnloadCandidates(
                    lastUse.keySet(),
                    lastUse::get,
                    nowMillis(),
                    adjustedIdleDuration,
                    hyperLock::withLong,
                    toUnload::add,
                    this::onDebug,
                    this::onError
            );
        } catch (Throwable e) {
            onError(e);
        } finally {
            trimSemaphore().release(DEFAULT_LOCK_SIZE);
        }
    }

    public synchronized int unloadTectonicPlate(int tectonicLimit) {
        ensureOpen();

        unloadSemaphore().acquireUninterruptibly(DEFAULT_LOCK_SIZE);
        try {
            return MantleLifecycleSupport.unloadRegions(
                    toUnload.size(),
                    tectonicLimit,
                    nowMillis(),
                    adjustedIdleDuration,
                    toUnload,
                    ioBurst::burst,
                    hyperLock::withLong,
                    loadedRegions::get,
                    lastUse::get,
                    toUnload::contains,
                    TectonicPlate::inUse,
                    this::use,
                    this::persistRegion,
                    (id, m) -> loadedRegions.remove(id, m),
                    lastUse::remove,
                    toUnload::remove,
                    (id, m) -> "Unloaded Tectonic Plate " + CacheKey.keyX(id) + " " + CacheKey.keyZ(id),
                    (id, m) -> "Tectonic Plate was added to unload while in use " + m.getX() + " " + m.getZ(),
                    id -> "Tectonic Plate was added to unload while not loaded " + CacheKey.keyX(id) + " " + CacheKey.keyZ(id),
                    this::onError,
                    this::onDebug
            );
        } catch (Throwable e) {
            onError(e);
        } finally {
            unloadSemaphore().release(DEFAULT_LOCK_SIZE);
        }

        return 0;
    }

    @RegionCoordinates
    private P get(int x, int z) {
        return accessRegion(x, z);
    }

    private CompletableFuture<P> getFuture(int x, int z) {
        return accessRegionFuture(x, z);
    }

    public void saveAll() {
        ensureOpen();
        flushLoadedRegions();
        loadedRegions.clear();
        lastUse.clear();
        toUnload.clear();
    }

    public void deleteChunkSlice(int x, int z, Class<?> type) {
        if (shouldRetainSlice(type)) {
            return;
        }

        deleteChunkSlice(getChunk(x, z), type);
    }

    public int getLoadedRegionCount() {
        return loadedRegions.size();
    }

    public int getUnloadRegionCount() {
        return toUnload.size();
    }

    public double getAdjustedIdleDuration() {
        return adjustedIdleDuration;
    }

    public boolean isLoaded(Chunk chunk) {
        return loadedRegions.containsKey(key(chunk.getX() >> 5, chunk.getZ() >> 5));
    }

    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return loadedRegions.containsKey(key(chunkX >> 5, chunkZ >> 5));
    }

    public KMap<Long, P> getLoadedRegions() {
        return loadedRegions;
    }

    @Override
    protected String regionName() {
        return "Tectonic Plate";
    }

    @Override
    protected String regionRetryName() {
        return "Mantle Region";
    }

    @Override
    protected CompletableFuture<P> loadRegionSafe(int x, int z) {
        return getSafe(x, z);
    }

    @Override
    protected P loadRegionBlocking(int x, int z) {
        return loadRegionNow(x, z);
    }

    @Override
    protected P getLoadedRegion(int x, int z) {
        return loadedRegions.get(key(x, z));
    }

    @Override
    protected boolean isRegionClosed(P region) {
        return region.isClosed();
    }

    @Override
    protected void markRegionUsed(int x, int z, P region) {
        use(key(x, z));
    }

    @Override
    protected void warn(String message) {
        onWarn(message);
    }

    @Override
    protected void report(Throwable throwable) {
        onError(throwable);
    }

    protected CompletableFuture<P> getSafe(int x, int z) {
        return ioBurst.completableFuture(() -> loadRegionNow(x, z));
    }

    private P loadRegionNow(int x, int z) {
        return hyperLock.withResult(x, z, () -> {
            Long k = key(x, z);
            use(k);

            P loaded = loadedRegions.get(k);
            if (loaded != null && !loaded.isClosed()) {
                return loaded;
            }

            File file = fileForRegion(dataFolder, x, z);
            if (file.exists()) {
                try {
                    P region = regionIO.read(file.getName());
                    if (region.getX() != x || region.getZ() != z) {
                        onWarn("Loaded Tectonic Plate " + x + "," + z + " but read it as " + region.getX() + "," + region.getZ() + ". Assuming " + x + "," + z);
                    }

                    loadedRegions.put(k, region);
                    onDebug("Loaded Tectonic Plate " + x + " " + z + " " + file.getName());
                    use(k);
                    return region;
                } catch (Throwable e) {
                    onWarn("Failed to read Tectonic Plate " + file.getAbsolutePath() + ", creating a new one.");
                    onError(e);

                    P fallback = createRegion(x, z);
                    loadedRegions.put(k, fallback);
                    onDebug("Created new Tectonic Plate (Due to Load Failure) " + x + " " + z);
                    use(k);
                    return fallback;
                }
            }

            P region = createRegion(x, z);
            loadedRegions.put(k, region);
            onDebug("Created new Tectonic Plate " + x + " " + z);
            use(k);
            return region;
        });
    }

    protected void use(long key) {
        lastUse.put(key, nowMillis());
        toUnload.remove(key);
    }

    protected long nowMillis() {
        return System.currentTimeMillis();
    }

    protected String formatDuration(double millis) {
        return millis + "ms";
    }

    protected void onDebug(String message) {
    }

    protected void onWarn(String message) {
        System.err.println(message);
    }

    protected void onError(Throwable throwable) {
        throwable.printStackTrace();
    }

    protected boolean shouldRetainSlice(Class<?> sliceType) {
        return false;
    }

    protected void deleteTemporaryFiles() {
        deleteDirectory(new File(dataFolder, ".tmp"));
    }

    private void flushLoadedRegions() {
        MantleLifecycleSupport.flushLoadedRegions(
                loadedRegions.size(),
                consumer -> loadedRegions.forEach((id, plate) -> consumer.accept(id, plate)),
                ioBurst::burst,
                this::persistRegion,
                (id, plate, e) -> {
                    onWarn("Failed to write Tectonic Plate " + CacheKey.keyX(id) + " " + CacheKey.keyZ(id));
                    onError(e);
                },
                this::onError
        );
    }

    private void persistRegion(long id, P plate) throws Exception {
        plate.close();
        regionIO.write(fileForRegion(dataFolder, id, false).getName(), plate);
        oldFileForRegion(dataFolder, id).delete();
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("The Mantle is closed");
        }
    }

    private static void deleteDirectory(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    deleteDirectory(child);
                } else {
                    child.delete();
                }
            }
        }

        file.delete();
    }

    public interface RegionIO<P> {
        P read(String name) throws Exception;

        void write(String name, P region) throws Exception;

        void close() throws Exception;
    }

    protected abstract P createRegion(int x, int z);

    protected abstract <T> void setChunkValue(C chunk, int x, int y, int z, T value);

    protected abstract <T> void removeChunkValue(C chunk, int x, int y, int z, Class<T> type);

    protected abstract <T> T getChunkValue(C chunk, int x, int y, int z, Class<T> type);

    protected abstract <T> void iterateChunkValues(C chunk, Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator);

    protected abstract void deleteChunkSlice(C chunk, Class<?> type);
}
