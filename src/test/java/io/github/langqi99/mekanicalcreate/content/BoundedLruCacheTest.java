package io.github.langqi99.mekanicalcreate.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BoundedLruCacheTest {
    @Test
    void evictsLeastRecentlyUsedEntry() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("first", 1);
        cache.put("second", 2);
        assertEquals(1, cache.get("first"));

        cache.put("third", 3);

        assertTrue(cache.containsKey("first"));
        assertFalse(cache.containsKey("second"));
        assertTrue(cache.containsKey("third"));
        assertEquals(2, cache.size());
    }

    @Test
    void replacingAnEntryRefreshesItsRecency() {
        BoundedLruCache<String, Integer> cache = new BoundedLruCache<>(2);
        cache.put("first", 1);
        cache.put("second", 2);
        cache.put("first", 10);
        cache.put("third", 3);

        assertEquals(10, cache.get("first"));
        assertFalse(cache.containsKey("second"));
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedLruCache<>(0));
    }
}
