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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBackedSetTest {
    @TempDir
    Path tempDir;

    private FileBackedSet<String> set;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test-set.bin").toFile();
        set = new FileBackedSet.Builder<String>(testFile).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (set != null) {
            set.close();
        }
    }

    @Test
    void testAdd() throws IOException {
        assertTrue(set.add("element1"));
        assertEquals(1, set.size());
        assertFalse(set.add("element1")); // Adding duplicate
        assertEquals(1, set.size());
    }

    @Test
    void testContains() throws IOException {
        set.add("test");
        assertTrue(set.contains("test"));
        assertFalse(set.contains("missing"));
    }

    @Test
    void testSize() throws IOException {
        assertEquals(0, set.size());
        set.add("a");
        set.add("b");
        set.add("c");
        assertEquals(3, set.size());
    }

    @Test
    void testIsEmpty() {
        assertTrue(set.isEmpty());
    }

    @Test
    void testIterator() throws IOException {
        set.add("x");
        set.add("y");
        set.add("z");
        int count = 0;
        for (String s : set) {
            assertNotNull(s);
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    void testGetFirst() throws IOException {
        set.add("a");
        set.add("b");
        assertEquals("a", set.getFirst());
    }

    @Test
    void testGetFirstEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> set.getFirst());
    }

    @Test
    void testGetLast() throws IOException {
        set.add("a");
        set.add("b");
        assertEquals("b", set.getLast());
    }

    @Test
    void testGetLastEmpty() {
        assertThrows(java.util.NoSuchElementException.class, () -> set.getLast());
    }

    @Test
    void testAddAll() throws IOException {
        java.util.Set<String> other = new java.util.HashSet<>();
        other.add("x");
        other.add("y");
        other.add("z");
        set.addAll(other);
        assertEquals(3, set.size());
        assertTrue(set.contains("x"));
    }

    @Test
    void testContainsAll() throws IOException {
        set.add("a");
        set.add("b");
        java.util.List<String> list = java.util.Arrays.asList("a", "b");
        assertTrue(set.containsAll(list));
    }

    @Test
    void testToArray() throws IOException {
        set.add("x");
        set.add("y");
        Object[] array = set.toArray();
        assertEquals(2, array.length);
    }

    @Test
    void testPersistence() throws IOException {
        set.add("persist1");
        set.add("persist2");
        set.close();

        FileBackedSet<String> reloaded = new FileBackedSet.Builder<String>(testFile).build();
        assertEquals(2, reloaded.size());
        assertTrue(reloaded.contains("persist1"));
        assertTrue(reloaded.contains("persist2"));
        reloaded.close();
    }

    @Test
    void testBuilderWithCustomSettings() throws IOException {
        File customFile = tempDir.resolve("custom-set.bin").toFile();
        FileBackedSet<String> customSet = new FileBackedSet.Builder<String>(customFile)
            .enableChecksums(false)
            .enableMmap(false)
            .enableCache(false)
            .enableBTreeIndex(false)
            .cacheSize(500)
            .cacheFlushMs(10000)
            .build();

        customSet.add("test");
        assertEquals(1, customSet.size());
        customSet.close();
    }

    @Test
    void testFlush() throws IOException {
        set.add("data");
        assertDoesNotThrow(() -> set.flush());
    }

    @Test
    void testCompact() throws IOException {
        set.add("item1");
        set.add("item2");
        set.add("item1"); // Duplicate won't be added
        assertDoesNotThrow(() -> set.compact());
        assertEquals(2, set.size());
    }

    @Test
    void testAddFirstUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> set.addFirst("test"));
    }

    @Test
    void testRemoveFirstUnsupported() {
        set.add("test");
        assertThrows(UnsupportedOperationException.class, () -> set.removeFirst());
    }

    @Test
    void testReversedView() {
        set.add("a");
        set.add("b");
        set.add("c");

        java.util.SequencedSet<String> reversed = set.reversed();

        // Test contains() works correctly on reversed view
        assertTrue(reversed.contains("a"));
        assertTrue(reversed.contains("b"));
        assertTrue(reversed.contains("c"));

        // Test iteration order is reversed
        java.util.Iterator<String> it = reversed.iterator();
        assertEquals("c", it.next());
        assertEquals("b", it.next());
        assertEquals("a", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testEmptySet() {
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
        assertFalse(set.contains("anything"));
    }

    @Test
    void testIteratorOnEmptySet() {
        int count = 0;
        for (String item : set) {
            count++;
        }
        assertEquals(0, count);
    }

    @Test
    void testToArrayWithType() {
        set.add("a");
        set.add("b");
        String[] array = set.toArray(new String[0]);
        assertEquals(2, array.length);
    }

    @Test
    void testRemoveAll() {
        set.add("a");
        set.add("b");
        set.add("c");
        java.util.Set<String> toRemove = new java.util.HashSet<>();
        toRemove.add("a");
        toRemove.add("c");
        // removeAll is not supported, should throw
        assertThrows(UnsupportedOperationException.class, () -> set.removeAll(toRemove));
    }

    @Test
    void testRetainAll() {
        set.add("a");
        set.add("b");
        set.add("c");
        java.util.Set<String> toKeep = new java.util.HashSet<>();
        toKeep.add("b");
        // retainAll is not supported, should throw
        assertThrows(UnsupportedOperationException.class, () -> set.retainAll(toKeep));
    }

    @Test
    void testBuilderNullPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedSet.Builder<String>(null).build());
    }

    @Test
    void testBuilderDirectoryPath() {
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedSet.Builder<String>(tempDir.toFile()).build());
    }

    @Test
    void testBuilderNegativeCacheSize() {
        File validFile = tempDir.resolve("cache-size-test.bin").toFile();
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedSet.Builder<String>(validFile).cacheSize(-1).build());
    }

    @Test
    void testBuilderNegativeFlushInterval() {
        File validFile = tempDir.resolve("flush-interval-test.bin").toFile();
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedSet.Builder<String>(validFile).cacheFlushMs(-100).build());
    }
}
