package org.flossware.jcollections.file.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
