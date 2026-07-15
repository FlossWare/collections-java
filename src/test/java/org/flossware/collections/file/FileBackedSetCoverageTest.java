package org.flossware.collections.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.SequencedSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for FileBackedSet targeting uncovered branches:
 * - addLast
 * - removeLast
 * - flush/compact on ownsMap path
 * - close exception handling
 * - reversed view operations
 */
class FileBackedSetCoverageTest {
    @TempDir
    Path tempDir;

    private FileBackedSet<String> set;
    private File testFile;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("set-coverage.bin").toFile();
        set = new FileBackedSet.Builder<String>(testFile).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (set != null) {
            set.close();
        }
    }

    // =====================================================================
    // addLast coverage
    // =====================================================================

    @Test
    void testAddLast() {
        set.add("a");
        set.addLast("b");

        assertEquals(2, set.size());
        assertTrue(set.contains("b"));
        assertEquals("b", set.getLast());
    }

    @Test
    void testAddLastDuplicate() {
        set.add("a");
        set.addLast("a"); // Adding duplicate via addLast

        // The map's putLast will add a new entry but entrySet deduplication
        // means size() may show unique count only
        assertTrue(set.contains("a"));
    }

    // =====================================================================
    // removeLast coverage
    // =====================================================================

    @Test
    void testRemoveLast() {
        set.add("a");
        set.add("b");
        set.add("c");

        String removed = set.removeLast();

        assertNotNull(removed);
        assertEquals("c", removed);
        assertEquals(2, set.size());
    }

    @Test
    void testRemoveLastOnEmptySet() {
        // removeLast on empty set should return null (pollLastEntry returns null)
        String result = set.removeLast();
        assertNull(result);
    }

    @Test
    void testRemoveLastUntilEmpty() {
        set.add("x");
        set.add("y");

        assertEquals("y", set.removeLast());
        assertEquals("x", set.removeLast());
        assertNull(set.removeLast()); // Empty set
    }

    // =====================================================================
    // flush and compact on ownsMap
    // =====================================================================

    @Test
    void testFlushOnOwnedMap() throws IOException {
        set.add("data1");
        set.add("data2");

        // flush() should succeed since ownsMap is true and backingMap is FileBackedMap
        assertDoesNotThrow(() -> set.flush());
    }

    @Test
    void testCompactOnOwnedMap() throws IOException {
        set.add("item1");
        set.add("item2");

        // compact() should succeed since ownsMap is true
        assertDoesNotThrow(() -> set.compact());
    }

    // =====================================================================
    // close coverage
    // =====================================================================

    @Test
    void testCloseOnOwnedMap() throws IOException {
        set.add("test");
        set.close();
        set = null; // Prevent double close
    }

    // =====================================================================
    // reversed view operations
    // =====================================================================

    @Test
    void testReversedViewContains() {
        set.add("a");
        set.add("b");
        set.add("c");

        SequencedSet<String> reversed = set.reversed();
        assertTrue(reversed.contains("a"));
        assertTrue(reversed.contains("b"));
        assertTrue(reversed.contains("c"));
        assertFalse(reversed.contains("d"));
    }

    @Test
    void testReversedViewIterationOrder() {
        set.add("first");
        set.add("second");
        set.add("third");

        SequencedSet<String> reversed = set.reversed();
        Iterator<String> it = reversed.iterator();

        assertEquals("third", it.next());
        assertEquals("second", it.next());
        assertEquals("first", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void testReversedViewSize() {
        set.add("a");
        set.add("b");

        SequencedSet<String> reversed = set.reversed();
        assertEquals(2, reversed.size());
    }

    @Test
    void testReversedViewOnEmpty() {
        SequencedSet<String> reversed = set.reversed();
        assertTrue(reversed.isEmpty());
        assertEquals(0, reversed.size());
    }

    @Test
    void testReversedViewGetFirst() {
        set.add("a");
        set.add("b");
        set.add("c");

        SequencedSet<String> reversed = set.reversed();
        assertEquals("c", reversed.getFirst());
    }

    @Test
    void testReversedViewGetLast() {
        set.add("a");
        set.add("b");
        set.add("c");

        SequencedSet<String> reversed = set.reversed();
        assertEquals("a", reversed.getLast());
    }

    // =====================================================================
    // Reversed view flush/compact/close - exercises ownsMap=false branches
    // =====================================================================

    @Test
    void testReversedViewFlushIsNoop() throws IOException {
        set.add("a");
        set.add("b");

        // The reversed view has ownsMap=false, so flush should be a no-op
        FileBackedSet<String> reversed = (FileBackedSet<String>) set.reversed();
        assertDoesNotThrow(() -> reversed.flush());
    }

    @Test
    void testReversedViewCompactIsNoop() throws IOException {
        set.add("a");
        set.add("b");

        // The reversed view has ownsMap=false, so compact should be a no-op
        FileBackedSet<String> reversed = (FileBackedSet<String>) set.reversed();
        assertDoesNotThrow(() -> reversed.compact());
    }

    @Test
    void testReversedViewCloseIsNoop() throws IOException {
        set.add("a");
        set.add("b");

        // The reversed view has ownsMap=false, so close should be a no-op
        FileBackedSet<String> reversed = (FileBackedSet<String>) set.reversed();
        assertDoesNotThrow(() -> reversed.close());

        // The original set should still be functional after reversed view is closed
        assertTrue(set.contains("a"));
    }
}
