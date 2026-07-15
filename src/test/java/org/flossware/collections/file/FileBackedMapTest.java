package org.flossware.collections.file;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void testMapWithoutBTreeIndex() throws IOException {
        File noBTreeFile = tempDir.resolve("no-btree.bin").toFile();
        FileBackedMap<String, String> noBTreeMap = new FileBackedMap.Builder<String, String>(noBTreeFile)
            .enableBTreeIndex(false)
            .build();

        noBTreeMap.put("key1", "value1");
        noBTreeMap.put("key2", "value2");

        assertTrue(noBTreeMap.containsKey("key1"));
        assertEquals("value1", noBTreeMap.get("key1"));
        assertEquals(2, noBTreeMap.size());

        noBTreeMap.close();
    }

    @Test
    void testBuilderAllOptions() throws IOException {
        File allOptionsFile = tempDir.resolve("all-options.bin").toFile();
        FileBackedMap<String, String> fullMap = new FileBackedMap.Builder<String, String>(allOptionsFile)
            .enableChecksums(false)
            .enableMmap(false)
            .enableCache(false)
            .enableBTreeIndex(false)
            .cacheSize(500)
            .cacheFlushMs(10000)
            .build();

        fullMap.put("test", "value");
        assertEquals("value", fullMap.get("test"));
        fullMap.close();
    }

    @Test
    void testEntrySetSize() throws IOException {
        map.put("a", "1");
        map.put("b", "2");
        assertEquals(2, map.entrySet().size());
    }

    @Test
    void testGetNonExistentKey() {
        assertFalse(map.containsKey("nonexistent"));
        assertEquals(null, map.get("nonexistent"));
    }

    @Test
    void testRemoveUnsupported() {
        map.put("key", "value");
        assertThrows(UnsupportedOperationException.class, () -> map.remove("key"));
    }

    @Test
    void testPutFirstUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> map.putFirst("key", "value"));
    }

    @Test
    void testPollFirstEntryUnsupported() {
        map.put("key", "value");
        assertThrows(UnsupportedOperationException.class, () -> map.pollFirstEntry());
    }

    @Test
    void testReversedView() {
        map.put("a", "first");
        map.put("b", "second");
        map.put("c", "third");

        java.util.SequencedMap<String, String> reversed = map.reversed();

        // Test get() works correctly on reversed view
        assertEquals("first", reversed.get("a"));
        assertEquals("second", reversed.get("b"));
        assertEquals("third", reversed.get("c"));

        // Test iteration order is reversed
        java.util.Iterator<String> it = reversed.keySet().iterator();
        assertEquals("c", it.next());
        assertEquals("b", it.next());
        assertEquals("a", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testReversedViewPutBlocked() {
        map.put("a", "1");
        java.util.SequencedMap<String, String> reversed = map.reversed();

        // put() should throw UnsupportedOperationException on reversed views
        assertThrows(UnsupportedOperationException.class, () ->
            reversed.put("b", "2"));
    }

    @Test
    void testMapValues() throws IOException {
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");
        assertTrue(map.values().contains("v1"));
        assertTrue(map.values().contains("v2"));
        assertTrue(map.values().contains("v3"));
        assertFalse(map.values().contains("v4"));
    }

    @Test
    void testEmptyMap() {
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.containsKey("anything"));
    }

    @Test
    void testIsEmpty() {
        assertTrue(map.isEmpty());
        map.put("key", "value");
        assertFalse(map.isEmpty());
    }

    @Test
    void testKeySetSize() {
        map.put("a", "1");
        map.put("b", "2");
        assertEquals(2, map.keySet().size());
    }

    @Test
    void testKeySetIterator() {
        map.put("x", "1");
        map.put("y", "2");
        int count = 0;
        for (String key : map.keySet()) {
            assertNotNull(key);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void testValuesIterator() {
        map.put("a", "111");
        map.put("b", "222");
        int count = 0;
        for (String value : map.values()) {
            assertNotNull(value);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void testBuilderNullPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedMap.Builder<String, String>(null).build());
    }

    @Test
    void testBuilderDirectoryPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedMap.Builder<String, String>(tempDir.toFile()).build());
    }

    @Test
    void testBuilderNegativeCacheSize() {
        File validFile = tempDir.resolve("cache-size-test.bin").toFile();
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedMap.Builder<String, String>(validFile).cacheSize(-1).build());
    }

    @Test
    void testBuilderNegativeFlushInterval() {
        File validFile = tempDir.resolve("flush-interval-test.bin").toFile();
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedMap.Builder<String, String>(validFile).cacheFlushMs(-100).build());
    }

    @Test
    void testFsyncEnabledMap() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-map.bin").toFile();
        try (FileBackedMap<String, String> fsyncMap = new FileBackedMap.Builder<String, String>(fsyncFile)
                .enableFsync(true)
                .enableBTreeIndex(true)
                .build()) {
            fsyncMap.put("key1", "value1");
            fsyncMap.put("key2", "value2");

            assertEquals("value1", fsyncMap.get("key1"));
            assertEquals("value2", fsyncMap.get("key2"));
            assertEquals(2, fsyncMap.size());
        }
    }

    @Test
    void testFsyncDefaultDisabledMap() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-default-map.bin").toFile();
        try (FileBackedMap<String, String> defaultMap = new FileBackedMap.Builder<String, String>(fsyncFile)
                .build()) {
            defaultMap.put("test", "val");
            // Default should not have fsync flag
            assertDoesNotThrow(() -> defaultMap.flush());
        }
    }

    @Test
    void testBuilderAcceptsFsyncOption() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-builder-map.bin").toFile();
        FileBackedMap.Builder<String, String> builder = new FileBackedMap.Builder<String, String>(fsyncFile)
            .enableFsync(true)
            .enableChecksums(true)
            .enableMmap(true);
        try (FileBackedMap<String, String> fsyncMap = builder.build()) {
            assertNotNull(fsyncMap);
            fsyncMap.put("key", "value");
            assertEquals("value", fsyncMap.get("key"));
        }
    }

    @Test
    void testFsyncEnabledPersistence() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-persist-map.bin").toFile();
        try (FileBackedMap<String, String> fsyncMap = new FileBackedMap.Builder<String, String>(fsyncFile)
                .enableFsync(true)
                .build()) {
            fsyncMap.put("durable", "data");
            fsyncMap.flush();
        }

        try (FileBackedMap<String, String> reopened = new FileBackedMap.Builder<String, String>(fsyncFile)
                .build()) {
            assertEquals("data", reopened.get("durable"));
        }
    }
}
