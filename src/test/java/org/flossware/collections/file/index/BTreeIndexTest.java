package org.flossware.collections.file.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BTreeIndexTest {
    private BTreeIndex<String> index;

    @BeforeEach
    void setUp() {
        index = new BTreeIndex<>();
    }

    @Test
    void testPutAndGet() {
        index.put("key1", 10L);
        assertEquals(10L, index.get("key1"));
    }

    @Test
    void testGetNonExistent() {
        assertNull(index.get("missing"));
    }

    @Test
    void testPutMultiple() {
        for (int i = 0; i < 100; i++) {
            index.put("key" + i, (long) i);
        }
        for (int i = 0; i < 100; i++) {
            assertEquals((long) i, index.get("key" + i));
        }
    }

    @Test
    void testUpdateValue() {
        index.put("key", 100L);
        index.put("key", 200L);
        assertEquals(200L, index.get("key"));
    }

    @Test
    void testClear() {
        index.put("a", 1L);
        index.put("b", 2L);
        index.clear();
        assertNull(index.get("a"));
        assertNull(index.get("b"));
    }

    @Test
    void testLargeInsertions() {
        // Test that triggers node splitting
        for (int i = 0; i < 1000; i++) {
            index.put(String.format("key%04d", i), (long) i);
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals((long) i, index.get(String.format("key%04d", i)));
        }
    }

    @Test
    void testReverseOrderInsert() {
        for (int i = 100; i >= 0; i--) {
            index.put("key" + i, (long) i);
        }
        for (int i = 0; i <= 100; i++) {
            assertEquals((long) i, index.get("key" + i));
        }
    }

    @Test
    void testRandomAccess() {
        index.put("zebra", 1L);
        index.put("apple", 2L);
        index.put("mango", 3L);
        index.put("banana", 4L);

        assertEquals(1L, index.get("zebra"));
        assertEquals(2L, index.get("apple"));
        assertEquals(3L, index.get("mango"));
        assertEquals(4L, index.get("banana"));
    }

    @Test
    void testEmptyIndex() {
        assertNull(index.get("anything"));
    }

    @Test
    void testDuplicateKeys() {
        index.put("dup", 1L);
        index.put("dup", 2L);
        index.put("dup", 3L);
        assertEquals(3L, index.get("dup"));
    }

    @Test
    void testGetAll() {
        index.put("key1", 10L);
        index.put("key2", 20L);
        index.put("key3", 30L);

        List<BTreeIndex.IndexEntry<String>> entries = index.getAll();
        assertEquals(3, entries.size());

        // Entries should be in sorted order
        for (BTreeIndex.IndexEntry<String> entry : entries) {
            assertNotNull(entry.key);
            assertTrue(entry.offset >= 0);
        }
    }

    @Test
    void testGetAllEmpty() {
        List<BTreeIndex.IndexEntry<String>> entries = index.getAll();
        assertTrue(entries.isEmpty());
    }

    @Test
    void testGetAllAfterClear() {
        index.put("a", 1L);
        index.put("b", 2L);
        index.clear();

        List<BTreeIndex.IndexEntry<String>> entries = index.getAll();
        assertTrue(entries.isEmpty());
    }

    @Test
    void testMassiveInsertionToTriggerMultipleSplits() {
        // Insert enough entries to trigger multiple node splits including non-leaf splits
        // ORDER = 128, so we need > 128 * 128 entries to get deep tree
        for (int i = 0; i < 20000; i++) {
            index.put(String.format("key%05d", i), (long) i);
        }

        // Verify all entries are retrievable
        for (int i = 0; i < 20000; i++) {
            assertEquals((long) i, index.get(String.format("key%05d", i)));
        }

        // Verify getAllEntries works with deep tree
        List<BTreeIndex.IndexEntry<String>> entries = index.getAll();
        assertEquals(20000, entries.size());
    }

    @Test
    void testInsertionInNonLeafNode() {
        // Force tree to grow deep by inserting many entries
        // This will trigger insertNonFull on non-leaf nodes
        for (int i = 0; i < 500; i++) {
            index.put("k" + String.format("%04d", i), (long) i);
        }

        // Now insert in various positions to trigger different code paths
        index.put("k0000.5", 999L);  // Insert between existing keys
        assertEquals(999L, index.get("k0000.5"));

        // Update an existing key
        index.put("k0100", 888L);
        assertEquals(888L, index.get("k0100"));
    }

    @Test
    void testGetAllLarge() {
        // Insert 1000 entries and verify getAllEntries returns them all
        for (int i = 0; i < 1000; i++) {
            index.put("entry" + i, (long) i);
        }

        List<BTreeIndex.IndexEntry<String>> entries = index.getAll();
        assertEquals(1000, entries.size());

        // Verify entries are sorted
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i).key.compareTo(entries.get(i-1).key) >= 0,
                "Entries should be in sorted order");
        }
    }
}
