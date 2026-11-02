package dev.chatchronicle.core;

import java.util.LinkedHashMap;
import java.util.function.Function;

/** Access-order cache owned by one thread. Never retains more than its configured capacity. */
public final class BoundedCache<K, V> {
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, .75f, true);
    private int capacity;
    public BoundedCache(int capacity) { resize(capacity); }
    public void resize(int capacity) {
        this.capacity = Math.max(0, capacity);
        while (entries.size() > this.capacity) entries.remove(entries.keySet().iterator().next());
    }
    public V get(K key, Function<K, V> loader) {
        V value = entries.get(key);
        if (value != null) return value;
        value = loader.apply(key);
        if (value != null) put(key, value);
        return value;
    }
    public void put(K key, V value) { entries.put(key, value); resize(capacity); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
}
