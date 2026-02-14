package art.arcane.volmlib.util.data;

import art.arcane.volmlib.util.data.base.KCacheBase;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KCache<K, V> extends KCacheBase<K, V> {
    public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    public KCache(CacheLoader<K, V> loader, long max) {
        super(loader, max);
    }

    public KCache(CacheLoader<K, V> loader, long max, boolean fastDump) {
        super(loader, max, fastDump);
    }

    @Override
    protected Caffeine<Object, Object> configureBuilder(Caffeine<Object, Object> builder) {
        return builder
                .scheduler(Scheduler.systemScheduler())
                .executor(EXECUTOR);
    }

    @Override
    public KCache<?, ?> getRawCache() {
        return this;
    }
}
