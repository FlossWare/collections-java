package org.flossware.collections.file.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteCacheTest {
    private WriteCache<String> cache;

    @BeforeEach
    void setUp() {
        cache = new WriteCache<>(5, 1000); // 5 items, 1 second flush
    }

    @Test
    void testPutAndGet() {
        cache.put(100L, "value1", "value1".getBytes());
        assertEquals("value1", cache.get(100L));
    }

    @Test
    void testGetNonExistent() {
        assertNull(cache.get(999L));
    }

    @Test
    void testGetPendingWrites() {
        cache.put(1L, "a", "a".getBytes());
        cache.put(2L, "b", "b".getBytes());
        List<WriteCache.CachedEntry<String>> pending = cache.getPendingWrites();
        assertEquals(2, pending.size());
    }

    @Test
    void testShouldFlushSize() {
        for (int i = 0; i < 5; i++) {
            cache.put(i, "item" + i, ("item" + i).getBytes());
        }
        assertTrue(cache.shouldFlush());
    }

    @Test
    void testShouldFlushTime() throws InterruptedException {
        cache.put(1L, "item", "item".getBytes());
        Thread.sleep(1100); // Wait longer than flush interval
        assertTrue(cache.shouldFlush());
    }

    @Test
    void testClear() {
        cache.put(1L, "x", "x".getBytes());
        cache.put(2L, "y", "y".getBytes());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());
        cache.put(1L, "item", "item".getBytes());
        assertEquals(1, cache.size());
    }

    @Test
    void testMultiplePutsToSameOffset() {
        cache.put(100L, "first", "first".getBytes());
        cache.put(100L, "second", "second".getBytes());
        assertEquals("second", cache.get(100L));
    }

    @Test
    void testCachedEntry() {
        byte[] data = "test".getBytes();
        WriteCache.CachedEntry<String> entry = new WriteCache.CachedEntry<>(100L, "test", data);
        assertEquals(100L, entry.offset);
        assertEquals("test", entry.value);
        assertNotNull(entry.serialized);
        assertTrue(entry.timestamp > 0);
    }

    @Test
    void testShouldNotFlushWhenEmpty() {
        assertFalse(cache.shouldFlush());
    }

    @Test
    void testAutoFlushOnSize() {
        for (int i = 0; i < 6; i++) {
            cache.put(i, "item" + i, ("item" + i).getBytes());
            if (i >= 4) {
                assertTrue(cache.shouldFlush());
            }
        }
    }

    @Test
    void testGetPendingWritesIfNeededWhenNeeded() {
        for (int i = 0; i < 5; i++) {
            cache.put(i, "item" + i, ("item" + i).getBytes());
        }
        List<WriteCache.CachedEntry<String>> pending = cache.getPendingWritesIfNeeded();
        assertEquals(5, pending.size());
    }

    @Test
    void testGetPendingWritesIfNeededWhenNotNeeded() {
        cache.put(1L, "item", "item".getBytes());
        List<WriteCache.CachedEntry<String>> pending = cache.getPendingWritesIfNeeded();
        assertTrue(pending.isEmpty());
    }

    @Test
    void testGetPendingWritesIfNeededByTime() throws InterruptedException {
        cache.put(1L, "item", "item".getBytes());
        Thread.sleep(1100); // Wait longer than flush interval
        List<WriteCache.CachedEntry<String>> pending = cache.getPendingWritesIfNeeded();
        assertEquals(1, pending.size());
    }

    @Test
    void testGetPendingWritesWhenEmpty() {
        List<WriteCache.CachedEntry<String>> pending = cache.getPendingWrites();
        assertTrue(pending.isEmpty());
    }

    @Test
    void testCloseCallsClear() {
        cache.put(1L, "x", "x".getBytes());
        cache.put(2L, "y", "y".getBytes());
        cache.close();
        assertEquals(0, cache.size());
    }

    @Test
    void testMaxPendingWritesException() {
        WriteCache<String> smallCache = new WriteCache<>(2, 10000);
        // maxPendingWrites = cacheSize * 10 = 20

        // Add entries without flushing
        for (int i = 0; i < 20; i++) {
            smallCache.put(i, "value" + i, ("value" + i).getBytes());
        }

        // 21st entry should throw exception
        assertThrows(IllegalStateException.class, () ->
            smallCache.put(20L, "overflow", "overflow".getBytes()));
    }
}
