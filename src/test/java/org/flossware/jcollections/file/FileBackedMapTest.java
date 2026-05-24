package org.flossware.jcollections.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBackedMapTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private FileBackedMap<String, String> map;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.bin").toFile();
        map = new FileBackedMap.Builder<String, String>(testFile)
            .enableBTreeIndex(true)
            .enableChecksums(true)
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (map != null) {
            map.close();
        }
    }

    @Test
    void testBTreeIndexedPutAndGet() {
        for (int i = 0; i < 100; i++) {
            map.put("Key" + i, "Value" + i);
        }

        assertEquals("Value50", map.get("Key50"));
        assertEquals(100, map.size());
    }

    @Test
    void testIndexCount() {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");

        assertEquals(3, map.getIndexedKeyCount());
    }

    @Test
    void testContainsKey() {
        map.put("TestKey", "TestValue");
        assertTrue(map.containsKey("TestKey"));
        assertFalse(map.containsKey("NonExistent"));
    }

    @Test
    void testPersistence() throws IOException {
        map.put("Key1", "Value1");
        map.put("Key2", "Value2");
        map.flush();
        map.close();

        FileBackedMap<String, String> newMap =
            new FileBackedMap.Builder<String, String>(testFile)
                .enableBTreeIndex(true)
                .build();

        assertEquals("Value1", newMap.get("Key1"));
        assertEquals("Value2", newMap.get("Key2"));
        newMap.close();
    }

    @Test
    void testFirstAndLastEntry() {
        map.put("A", "1");
        map.put("B", "2");
        map.put("C", "3");

        assertNotNull(map.firstEntry());
        assertNotNull(map.lastEntry());
    }

    @Test
    void testFlush() throws IOException {
        map.put("Test", "Value");
        assertDoesNotThrow(() -> map.flush());
    }

    @Test
    void testCompactWithDuplicates() throws IOException {
        map.put("key1", "value1");
        map.put("key2", "value2");
        map.put("key1", "updated1");
        map.put("key3", "value3");
        map.compact();
        assertEquals(3, map.size());
        assertEquals("updated1", map.get("key1"));
    }

    @Test
    void testReversed() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        map.put("c", "3");
        java.util.SequencedMap<String, String> reversed = map.reversed();
        java.util.Iterator<String> it = reversed.keySet().iterator();
        assertEquals("c", it.next());
        assertEquals("b", it.next());
        assertEquals("a", it.next());
    }

    @Test
    void testFirstEntry() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        java.util.Map.Entry<String, String> first = map.firstEntry();
        assertNotNull(first);
        assertEquals("a", first.getKey());
    }

    @Test
    void testLastEntry() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        java.util.Map.Entry<String, String> last = map.lastEntry();
        assertNotNull(last);
        assertEquals("b", last.getKey());
    }

    @Test
    void testPollFirstEntry() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        java.util.Map.Entry<String, String> first = map.pollFirstEntry();
        assertEquals("a", first.getKey());
        assertEquals(1, map.size());
        assertFalse(map.containsKey("a"));
    }

    @Test
    void testPollLastEntry() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        java.util.Map.Entry<String, String> last = map.pollLastEntry();
        assertEquals("b", last.getKey());
        assertEquals(1, map.size());
        assertFalse(map.containsKey("b"));
    }

    @Test
    void testPutAll() throws IOException {
        java.util.Map<String, String> otherMap = new java.util.HashMap<>();
        otherMap.put("x", "10");
        otherMap.put("y", "20");
        otherMap.put("z", "30");
        map.putAll(otherMap);
        assertEquals(3, map.size());
        assertEquals("10", map.get("x"));
    }

    @Test
    void testKeySetContains() throws IOException {
        map.put("key1", "value1");
        assertTrue(map.keySet().contains("key1"));
        assertFalse(map.keySet().contains("key2"));
    }

    @Test
    void testValuesContains() throws IOException {
        map.put("key1", "value1");
        assertTrue(map.values().contains("value1"));
        assertFalse(map.values().contains("value2"));
    }

    @Test
    void testEntrySetIterator() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        int count = 0;
        for (java.util.Map.Entry<String, String> entry : map.entrySet()) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void testClearAndIsEmpty() throws IOException {
        map.put("key1", "value1");
        assertFalse(map.isEmpty());
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }
}
