package dev.chatchronicle.core;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BoundedCacheTest {
    @Test void repeatedReadsReuseValuesAndEvictionKeepsRecentlyUsedEntries() {
        var loads = new AtomicInteger(); var cache = new BoundedCache<String, Object>(2);
        java.util.function.Function<String, Object> loader = key -> { loads.incrementAndGet(); return new Object(); };
        Object first = cache.get("first", loader); cache.get("second", loader);
        assertSame(first, cache.get("first", loader));
        cache.get("third", loader);
        assertSame(first, cache.get("first", loader));
        assertEquals(3, loads.get());
        cache.get("second", loader); assertEquals(4, loads.get()); assertEquals(2, cache.size());
        cache.resize(1); assertEquals(1, cache.size());
        cache.clear(); assertEquals(0, cache.size());
    }
    @Test void disabledCacheAndFailedLoadsDoNotRetainEntries() {
        var cache = new BoundedCache<String, String>(0);
        assertEquals("one", cache.get("key", k -> "one"));
        assertEquals("two", cache.get("key", k -> "two")); assertEquals(0, cache.size());
        cache.resize(2);
        assertThrows(IllegalStateException.class, () -> cache.get("key", k -> { throw new IllegalStateException(); }));
        assertEquals("recovered", cache.get("key", k -> "recovered"));
    }
}
