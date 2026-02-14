package art.arcane.volmlib.util.data.base;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class KCacheBase<K, V> {
    private final long max;
    private final boolean fastDump;
    private final LoadingCache<K, V> cache;
    private CacheLoader<K, V> loader;

    public KCacheBase(CacheLoader<K, V> loader, long max) {
        this(loader, max, false);
    }

    public KCacheBase(CacheLoader<K, V> loader, long max, boolean fastDump) {
        this.max = max;
        this.fastDump = fastDump;
        this.loader = loader;
        this.cache = create(loader);
    }

    protected Caffeine<Object, Object> configureBuilder(Caffeine<Object, Object> builder) {
        return builder;
    }

    @SuppressWarnings("unchecked")
    private LoadingCache<K, V> create(CacheLoader<K, V> loader) {
        Caffeine<Object, Object> builder = configureBuilder(
                Caffeine.newBuilder()
                        .maximumSize(max)
                        .initialCapacity((int) max));

        return (LoadingCache<K, V>) builder.build(key -> loader == null ? null : loader.load((K) key));
    }

    public void setLoader(CacheLoader<K, V> loader) {
        this.loader = loader;
    }

    public void invalidate(K k) {
        cache.invalidate(k);
    }

    public void invalidate() {
        cache.invalidateAll();
    }

    public V get(K k) {
        return cache.get(k);
    }

    public long getSize() {
        return cache.estimatedSize();
    }

    public KCacheBase<?, ?> getRawCache() {
        return this;
    }

    public long getMaxSize() {
        return max;
    }

    public boolean isClosed() {
        return false;
    }

    public boolean isFastDump() {
        return fastDump;
    }

    public CacheLoader<K, V> getLoader() {
        return loader;
    }

    public boolean contains(K next) {
        return cache.getIfPresent(next) != null;
    }
}
