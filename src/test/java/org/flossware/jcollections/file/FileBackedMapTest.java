package org.flossware.jcollections.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
}
