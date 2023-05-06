package www.ontologyutils.toolbox;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements a simple least-recently-used cache by extending the
 * {@code LinkedHashMap}. Most of the functionality is already provided by
 * {@code LinkedHashMap}, only the method {@code removeEldestEntry} must be
 * overwritten to specify a maximal cache size.
 */
public class LruCache<K, V> extends LinkedHashMap<K, V> {
    private int cacheSize;

    /**
     * Create a new cache with the specified size.
     *
     * @param cacheSize
     */
    public LruCache(int cacheSize) {
        super(cacheSize != Integer.MAX_VALUE ? cacheSize * 2 : 256, 0.75f, cacheSize != Integer.MAX_VALUE);
        this.cacheSize = cacheSize;
    }

    /**
     * Wrap the given function {@code function} using a {@code LruCache} with a
     * maximum number of {@code cacheSize} entries.
     *
     * @param <K>
     * @param <V>
     * @param function
     * @param cacheSize
     * @return The wrapped function.
     */
    public static <K, V> Function<K, V> wrapFunction(Function<K, V> function, int cacheSize) {
        var cache = new LruCache<K, V>(cacheSize);
        return input -> {
            return cache.computeIfAbsent(input, function);
        };
    }

    /**
     * Wrap the given function {@code function} using a {@code LruCache} with some
     * unspecified maximum size.
     *
     * @param <K>
     * @param <V>
     * @param function
     * @return The wrapped function.
     */
    public static <K, V> Function<K, V> wrapFunction(Function<K, V> function) {
        return wrapFunction(function, 4096);
    }

    /**
     * Because streams can not be cached directly, we provide this utility that
     * converts the stream to a list, and then back to a stream whenever needed.
     *
     * @param <K>
     * @param <V>
     * @param function
     * @param cacheSize
     *            The maximum number of entries in the cache.
     * @return The wrapped function.
     */
    public static <K, V> Function<K, Stream<V>> wrapStreamFunction(Function<K, Stream<V>> function, int cacheSize) {
        var cached = wrapFunction((K input) -> function.apply(input).collect(Collectors.toList()), cacheSize);
        return input -> cached.apply(input).stream();
    }

    /**
     * Because streams can not be cached directly, we provide this utility that
     * converts the stream to a list, and then back to a stream whenever needed.
     *
     * @param <K>
     * @param <V>
     * @param function
     * @return The wrapped function.
     */
    public static <K, V> Function<K, Stream<V>> wrapStreamFunction(Function<K, Stream<V>> function) {
        var cached = wrapFunction((K input) -> function.apply(input).collect(Collectors.toList()));
        return input -> cached.apply(input).stream();
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        if (cacheSize == Integer.MAX_VALUE) {
            return false;
        }
        return size() > cacheSize;
    }
}
