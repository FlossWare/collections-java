package org.flossware.collections.file.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for WriteCache targeting:
 * - LRU eviction behavior (cache size limit triggers removeEldestEntry)
 * - getPendingWritesIfNeeded time-based flush
 * - getPendingWritesIfNeeded when empty
 * - Pending writes boundary conditions
 */
class WriteCacheCoverageTest {

    // =====================================================================
    // LRU eviction - removeEldestEntry (L41-43)
    // =====================================================================

    @Test
    void testLruEvictionWhenCacheExceedsSize() {
        // Cache size of 3 - adding 5 items should evict 2 oldest
        WriteCache<String> cache = new WriteCache<>(3, 60000);

        cache.put(1L, "first", "first".getBytes());
        cache.put(2L, "second", "second".getBytes());
        cache.put(3L, "third", "third".getBytes());

        // All three should be in cache
        assertEquals("first", cache.get(1L));
        assertEquals("second", cache.get(2L));
        assertEquals("third", cache.get(3L));
        assertEquals(3, cache.size());

        // Adding a 4th entry should evict the least recently used
        cache.put(4L, "fourth", "fourth".getBytes());

        // Cache size should still be 3 (one evicted)
        assertEquals(3, cache.size());

        // Fourth should be present
        assertEquals("fourth", cache.get(4L));
    }

    @Test
    void testLruEvictionOrder() {
        WriteCache<String> cache = new WriteCache<>(2, 60000);

        cache.put(1L, "A", "A".getBytes());
        cache.put(2L, "B", "B".getBytes());

        // Access "A" to make it most recently used
        cache.get(1L);

        // Add "C" - should evict "B" (LRU), not "A"
        cache.put(3L, "C", "C".getBytes());

        // "A" should still be there (was recently accessed)
        assertEquals("A", cache.get(1L));
        // "C" should be there (just added)
        assertEquals("C", cache.get(3L));
        // "B" should be evicted
        assertNull(cache.get(2L));
    }

    // =====================================================================
    // getPendingWritesIfNeeded - time-based flush (L122-128)
    // =====================================================================

    @Test
    void testGetPendingWritesIfNeededByTime() throws InterruptedException {
        // Cache with very short time threshold (1ms)
        WriteCache<String> cache = new WriteCache<>(1000, 1);

        cache.put(1L, "item", "item".getBytes());

        // Wait for the time threshold to pass
        Thread.sleep(10);

        // Should trigger time-based flush
        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWritesIfNeeded();

        // Should have returned the pending write
        assertEquals(1, writes.size());
        assertEquals("item", writes.get(0).value);
    }

    @Test
    void testGetPendingWritesIfNeededReturnsEmptyWhenNotNeeded() {
        // Cache with large thresholds - neither size nor time should trigger
        WriteCache<String> cache = new WriteCache<>(1000, 60000);

        cache.put(1L, "item", "item".getBytes());

        // Neither size (1 < 1000) nor time (just added) should trigger flush
        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWritesIfNeeded();

        assertTrue(writes.isEmpty());
    }

    @Test
    void testGetPendingWritesIfNeededBySize() {
        // Cache with size threshold of 2
        WriteCache<String> cache = new WriteCache<>(2, 60000);

        cache.put(1L, "A", "A".getBytes());
        cache.put(2L, "B", "B".getBytes());

        // Size threshold met (2 >= 2)
        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWritesIfNeeded();

        assertEquals(2, writes.size());
    }

    @Test
    void testGetPendingWritesIfNeededEmptyCache() {
        WriteCache<String> cache = new WriteCache<>(10, 60000);

        // No pending writes
        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWritesIfNeeded();
        assertTrue(writes.isEmpty());
    }

    @Test
    void testGetPendingWritesIfNeededClearsPendingList() {
        WriteCache<String> cache = new WriteCache<>(2, 60000);

        cache.put(1L, "A", "A".getBytes());
        cache.put(2L, "B", "B".getBytes());

        // First call returns writes
        List<WriteCache.CachedEntry<String>> first = cache.getPendingWritesIfNeeded();
        assertEquals(2, first.size());

        // Second call should return empty (pending list was cleared)
        List<WriteCache.CachedEntry<String>> second = cache.getPendingWritesIfNeeded();
        assertTrue(second.isEmpty());
    }

    // =====================================================================
    // getPendingWrites - basic behavior
    // =====================================================================

    @Test
    void testGetPendingWritesEmptyReturnsEmptyList() {
        WriteCache<String> cache = new WriteCache<>(10, 60000);

        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWrites();
        assertNotNull(writes);
        assertTrue(writes.isEmpty());
    }

    @Test
    void testGetPendingWritesReturnAndClears() {
        WriteCache<String> cache = new WriteCache<>(10, 60000);

        cache.put(1L, "X", "X".getBytes());
        cache.put(2L, "Y", "Y".getBytes());

        List<WriteCache.CachedEntry<String>> writes = cache.getPendingWrites();
        assertEquals(2, writes.size());

        // After getPendingWrites, list should be cleared
        List<WriteCache.CachedEntry<String>> again = cache.getPendingWrites();
        assertTrue(again.isEmpty());
    }

    // =====================================================================
    // shouldFlush edge cases
    // =====================================================================

    @Test
    void testShouldFlushBySize() {
        WriteCache<String> cache = new WriteCache<>(2, 60000);

        assertFalse(cache.shouldFlush());

        cache.put(1L, "A", "A".getBytes());
        assertFalse(cache.shouldFlush()); // 1 < 2

        cache.put(2L, "B", "B".getBytes());
        assertTrue(cache.shouldFlush()); // 2 >= 2
    }

    @Test
    void testShouldFlushByTimeWithEmptyPending() throws InterruptedException {
        WriteCache<String> cache = new WriteCache<>(100, 1); // 1ms threshold

        // Even if time has passed, empty pending writes should not trigger
        Thread.sleep(10);
        assertFalse(cache.shouldFlush()); // Empty pending writes
    }

    @Test
    void testShouldFlushByTimeWithPendingWrites() throws InterruptedException {
        WriteCache<String> cache = new WriteCache<>(100, 1);

        cache.put(1L, "item", "item".getBytes());
        Thread.sleep(10);

        assertTrue(cache.shouldFlush());
    }

    // =====================================================================
    // CachedEntry timestamp
    // =====================================================================

    @Test
    void testCachedEntryHasTimestamp() {
        long before = System.currentTimeMillis();
        WriteCache.CachedEntry<String> entry = new WriteCache.CachedEntry<>(1L, "val", "val".getBytes());
        long after = System.currentTimeMillis();

        assertTrue(entry.timestamp >= before);
        assertTrue(entry.timestamp <= after);
        assertEquals(1L, entry.offset);
        assertEquals("val", entry.value);
    }

    // =====================================================================
    // close
    // =====================================================================

    @Test
    void testCloseCallsClear() {
        WriteCache<String> cache = new WriteCache<>(10, 60000);

        cache.put(1L, "test", "test".getBytes());
        assertEquals(1, cache.size());

        cache.close();

        assertEquals(0, cache.size());
        assertNull(cache.get(1L));
    }
}
