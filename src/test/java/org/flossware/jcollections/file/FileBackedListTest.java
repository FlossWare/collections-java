package org.flossware.jcollections.file;

import org.flossware.jcollections.file.format.FileHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBackedListTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private FileBackedList<String> list;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.bin").toFile();
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(true)
            .enableCache(true)
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (list != null) {
            list.close();
        }
    }

    @Test
    void testFileHeaderVersion() {
        assertEquals(FileHeader.VERSION_2, list.getHeader().getVersion());
    }

    @Test
    void testChecksumsEnabled() {
        assertTrue(list.getHeader().hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED));
    }

    @Test
    void testMmapEnabled() {
        assertTrue(list.getHeader().hasFlag(FileHeader.FLAG_MMAP_ENABLED));
    }

    @Test
    void testAddAndGet() {
        list.add("First");
        list.add("Second");
        list.add("Third");

        assertEquals(3, list.size());
        assertEquals("First", list.get(0));
        assertEquals("Second", list.get(1));
        assertEquals("Third", list.get(2));
    }

    @Test
    void testPersistence() throws IOException {
        list.add("Persist1");
        list.add("Persist2");
        list.flush();
        list.close();

        FileBackedList<String> newList = new FileBackedList.Builder<String>(testFile).build();
        assertEquals(2, newList.size());
        assertEquals("Persist1", newList.get(0));
        assertEquals("Persist2", newList.get(1));
        newList.close();
    }

    @Test
    void testCaching() throws IOException {
        File cacheTestFile = tempDir.resolve("cache-test.bin").toFile();
        FileBackedList<String> cachedList = new FileBackedList.Builder<String>(cacheTestFile)
            .enableCache(true)
            .cacheSize(10)
            .build();

        for (int i = 0; i < 20; i++) {
            cachedList.add("Item" + i);
        }

        assertEquals("Item10", cachedList.get(10));
        cachedList.close();
    }

    @Test
    void testFlush() throws IOException {
        list.add("Test");
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testCompact() throws IOException {
        list.add("Test");
        assertDoesNotThrow(() -> list.compact());
    }

    @Test
    void testAddFirst() throws IOException {
        list.add("Second");
        list.add("Third");
        list.addFirst("First");
        assertEquals("First", list.get(0));
        assertEquals("Second", list.get(1));
        assertEquals(3, list.size());
    }

    @Test
    void testAddLast() throws IOException {
        list.add("First");
        list.add("Second");
        list.addLast("Last");
        assertEquals("Last", list.get(2));
        assertEquals(3, list.size());
    }

    @Test
    void testRemoveFirst() throws IOException {
        list.add("First");
        list.add("Second");
        list.add("Third");
        String removed = list.removeFirst();
        assertEquals("First", removed);
        assertEquals(2, list.size());
        assertEquals("Second", list.get(0));
    }

    @Test
    void testRemoveFirstEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> list.removeFirst());
    }

    @Test
    void testRemoveLast() throws IOException {
        list.add("First");
        list.add("Second");
        list.add("Third");
        String removed = list.removeLast();
        assertEquals("Third", removed);
        assertEquals(2, list.size());
        assertEquals("Second", list.get(1));
    }

    @Test
    void testRemoveLastEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> list.removeLast());
    }

    @Test
    void testGetFilePath() {
        assertNotNull(list.getFilePath());
        assertTrue(list.getFilePath().exists());
    }

    @Test
    void testGetFirstEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> list.getFirst());
    }

    @Test
    void testGetLastEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> list.getLast());
    }

    @Test
    void testReversed() throws IOException {
        list.add("First");
        list.add("Second");
        list.add("Third");
        List<String> reversed = (List<String>) list.reversed();
        assertEquals("Third", reversed.get(0));
        assertEquals("Second", reversed.get(1));
        assertEquals("First", reversed.get(2));
    }

    @Test
    void testGetWithInvalidIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(100));
    }

    @Test
    void testMultipleCacheFlushs() throws IOException {
        for (int i = 0; i < 1500; i++) {
            list.add("Item" + i);
        }
        list.flush();
        assertEquals(1500, list.size());
    }

    @Test
    void testLargeFile() throws IOException {
        for (int i = 0; i < 10000; i++) {
            list.add("LargeItem" + i);
        }
        assertEquals(10000, list.size());
        assertEquals("LargeItem5000", list.get(5000));
    }
}
