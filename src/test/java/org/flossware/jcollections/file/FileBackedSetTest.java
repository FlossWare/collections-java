package org.flossware.jcollections.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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
    void testRemove() throws IOException {
        set.add("item");
        assertTrue(set.remove("item"));
        assertFalse(set.contains("item"));
        assertFalse(set.remove("nonexistent"));
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
    void testClear() throws IOException {
        set.add("a");
        set.add("b");
        set.clear();
        assertTrue(set.isEmpty());
        assertEquals(0, set.size());
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
    void testRemoveFirst() throws IOException {
        set.add("first");
        set.add("second");
        assertEquals("first", set.removeFirst());
        assertEquals(1, set.size());
        assertFalse(set.contains("first"));
    }

    @Test
    void testRemoveLast() throws IOException {
        set.add("first");
        set.add("last");
        assertEquals("last", set.removeLast());
        assertEquals(1, set.size());
        assertFalse(set.contains("last"));
    }

    @Test
    void testReversed() throws IOException {
        set.add("a");
        set.add("b");
        set.add("c");
        java.util.SequencedSet<String> reversed = set.reversed();
        java.util.Iterator<String> it = reversed.iterator();
        assertEquals("c", it.next());
        assertEquals("b", it.next());
        assertEquals("a", it.next());
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
    void testRemoveAll() throws IOException {
        set.add("a");
        set.add("b");
        set.add("c");
        java.util.List<String> toRemove = java.util.Arrays.asList("a", "c");
        set.removeAll(toRemove);
        assertEquals(1, set.size());
        assertTrue(set.contains("b"));
    }

    @Test
    void testRetainAll() throws IOException {
        set.add("a");
        set.add("b");
        set.add("c");
        java.util.List<String> toKeep = java.util.Arrays.asList("b", "c");
        set.retainAll(toKeep);
        assertEquals(2, set.size());
        assertFalse(set.contains("a"));
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
}
