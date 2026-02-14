package art.arcane.volmlib.util.mantle;

import art.arcane.volmlib.util.parallel.BurstExecutorSupport;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

public final class MantleLifecycleSupport {
    private MantleLifecycleSupport() {
    }

    public static double adjustIdleDuration(double baseIdleDuration, int loadedRegions, int tectonicLimit) {
        double idleDuration = baseIdleDuration;
        if (loadedRegions > tectonicLimit) {
            idleDuration = Math.max(idleDuration - (1000 * (((loadedRegions - tectonicLimit) / (double) tectonicLimit) * 100) * 0.4), 4000);
        }
        return idleDuration;
    }

    public static void collectUnloadCandidates(Iterable<Long> lastUseKeys,
                                               LongLookup lastUseLookup,
                                               double now,
                                               double idleDuration,
                                               LongLocker locker,
                                               LongConsumer markToUnload,
                                               Consumer<String> debug,
                                               Consumer<Throwable> report) {
        double unloadTime = now - idleDuration;
        try {
            for (long id : lastUseKeys) {
                locker.with(id, () -> {
                    Long lastUseTime = lastUseLookup.get(id);
                    if (lastUseTime != null && lastUseTime < unloadTime) {
                        markToUnload.accept(id);
                        if (debug != null) {
                            debug.accept("Tectonic Region added to unload");
                        }
                    }
                });
            }
        } catch (Throwable e) {
            if (report != null) {
                report.accept(e);
            }
        }
    }

    public static <P> int unloadRegions(int unloadCount,
                                        int tectonicLimit,
                                        double now,
                                        double adjustedIdleDuration,
                                        Iterable<Long> unloadKeys,
                                        BurstFactory burstFactory,
                                        LongLocker locker,
                                        RegionLookup<P> regionLookup,
                                        LongLookup lastUseLookup,
                                        LongPredicate unloadContains,
                                        RegionInUse<P> regionInUse,
                                        LongConsumer touch,
                                        RegionPersist<P> persist,
                                        RegionRemove<P> removeRegion,
                                        LongConsumer removeLastUse,
                                        LongConsumer removeUnload,
                                        RegionMessage<P> unloadedMessage,
                                        RegionMessage<P> inUseMessage,
                                        LongMessage notLoadedMessage,
                                        Consumer<Throwable> report,
                                        Consumer<String> debug) {
        AtomicInteger i = new AtomicInteger();
        BurstExecutorSupport burst = burstFactory.create(unloadCount);
        burst.setMulticore(unloadCount > tectonicLimit);
        double unloadTime = now - adjustedIdleDuration;

        try {
            for (long id : unloadKeys) {
                burst.queue(() -> locker.with(id, () -> {
                    P region = regionLookup.get(id);
                    if (region == null) {
                        if (debug != null && notLoadedMessage != null) {
                            debug.accept(notLoadedMessage.message(id));
                        }
                        removeUnload.accept(id);
                        return;
                    }

                    long used = lastUseLookup.getOrDefault(id, 0L);
                    if (!unloadContains.test(id) || used >= unloadTime) {
                        return;
                    }

                    if (regionInUse.test(region)) {
                        if (debug != null && inUseMessage != null) {
                            debug.accept(inUseMessage.message(id, region));
                        }
                        touch.accept(id);
                        return;
                    }

                    try {
                        persist.persist(id, region);
                        removeRegion.remove(id, region);
                        removeLastUse.accept(id);
                        removeUnload.accept(id);
                        i.incrementAndGet();
                        if (debug != null && unloadedMessage != null) {
                            debug.accept(unloadedMessage.message(id, region));
                        }
                    } catch (Throwable e) {
                        if (report != null) {
                            report.accept(e);
                        }
                    }
                }));
            }
            burst.complete();
        } catch (Throwable e) {
            if (report != null) {
                report.accept(e);
            }
            burst.complete();
        }

        return i.get();
    }

    public static <P> void flushLoadedRegions(int estimate,
                                              RegionEntries<P> entries,
                                              BurstFactory burstFactory,
                                              RegionPersist<P> persist,
                                              RegionError<P> onRegionError,
                                              Consumer<Throwable> report) {
        BurstExecutorSupport burst = burstFactory.create(estimate);
        try {
            entries.forEach((id, region) -> burst.queue(() -> {
                try {
                    persist.persist(id, region);
                } catch (Throwable e) {
                    if (onRegionError != null) {
                        onRegionError.accept(id, region, e);
                    }
                }
            }));
            burst.complete();
        } catch (Throwable e) {
            if (report != null) {
                report.accept(e);
            }
            burst.complete();
        }
    }

    @FunctionalInterface
    public interface BurstFactory {
        BurstExecutorSupport create(int estimate);
    }

    @FunctionalInterface
    public interface LongLocker {
        void with(long id, Runnable task);
    }

    @FunctionalInterface
    public interface LongLookup {
        Long get(long id);

        default long getOrDefault(long id, long defaultValue) {
            Long v = get(id);
            return v == null ? defaultValue : v;
        }
    }

    @FunctionalInterface
    public interface RegionLookup<P> {
        P get(long id);
    }

    @FunctionalInterface
    public interface RegionInUse<P> {
        boolean test(P region);
    }

    @FunctionalInterface
    public interface RegionPersist<P> {
        void persist(long id, P region) throws Exception;
    }

    @FunctionalInterface
    public interface RegionRemove<P> {
        void remove(long id, P region);
    }

    @FunctionalInterface
    public interface LongMessage {
        String message(long id);
    }

    @FunctionalInterface
    public interface RegionMessage<P> {
        String message(long id, P region);
    }

    @FunctionalInterface
    public interface RegionConsumer<P> {
        void accept(long id, P region);
    }

    @FunctionalInterface
    public interface RegionEntries<P> {
        void forEach(RegionConsumer<P> consumer);
    }

    @FunctionalInterface
    public interface RegionError<P> {
        void accept(long id, P region, Throwable error);
    }
}
