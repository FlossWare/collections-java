package org.flossware.collections.file.primitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LongListTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private LongList list;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.bin").toFile();
        list = new LongList(testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (list != null) {
            list.close();
        }
    }

    @Test
    void testAddAndGet() {
        list.add(10L);
        list.add(20L);
        list.add(30L);

        assertEquals(3, list.size());
        assertEquals(10L, list.get(0));
        assertEquals(20L, list.get(1));
        assertEquals(30L, list.get(2));
    }

    @Test
    void testLargeDataset() {
        for (int i = 0; i < 10000; i++) {
            list.add((long) i * 2);
        }

        assertEquals(10000, list.size());
        assertEquals(0L, list.get(0));
        assertEquals(9999L * 2, list.get(9999));
    }

    @Test
    void testPersistence() throws IOException {
        list.add(100L);
        list.add(200L);
        list.flush();
        list.close();

        LongList newList = new LongList(testFile);
        assertEquals(2, newList.size());
        assertEquals(100L, newList.get(0));
        assertEquals(200L, newList.get(1));
        newList.close();
    }

    @Test
    void testFlush() {
        list.add(42L);
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testIndexOutOfBounds() {
        list.add(10L);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    void testLargeCapacity() throws IOException {
        for (int i = 0; i < 10000; i++) {
            list.add((long) i);
        }
        assertEquals(10000, list.size());
        assertEquals(5000L, list.get(5000));
    }

    @Test
    void testMultipleFlush() throws IOException {
        list.add(1L);
        list.add(2L);
        list.flush();
        list.add(3L);
        list.add(4L);
        list.flush();
        assertEquals(4, list.size());
    }

    @Test
    void testNegativeNumbers() throws IOException {
        list.add(-100L);
        list.add(-1L);
        list.add(0L);
        list.add(1L);
        list.add(100L);
        assertEquals(-100L, list.get(0));
        assertEquals(100L, list.get(4));
    }

    @Test
    void testCloseAndReopen() throws IOException {
        list.add(111L);
        list.add(222L);
        list.close();

        LongList reopened = new LongList(testFile);
        assertEquals(2, reopened.size());
        assertEquals(111L, reopened.get(0));
        assertEquals(222L, reopened.get(1));
        reopened.close();
    }

    @Test
    void testMultipleClose() throws IOException {
        list.add(42L);
        list.close();
        assertDoesNotThrow(() -> list.close()); // Should not throw on second close
    }

    @Test
    void testEmptyList() {
        assertEquals(0, list.size());
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
    }

    @Test
    void testZeroValue() {
        list.add(0L);
        assertEquals(0L, list.get(0));
    }

    @Test
    void testMaxAndMinLong() {
        list.add(Long.MAX_VALUE);
        list.add(Long.MIN_VALUE);
        assertEquals(Long.MAX_VALUE, list.get(0));
        assertEquals(Long.MIN_VALUE, list.get(1));
    }

    @Test
    void testLargeValues() {
        long largePositive = 1_000_000_000_000L;
        long largeNegative = -1_000_000_000_000L;
        list.add(largePositive);
        list.add(largeNegative);
        assertEquals(largePositive, list.get(0));
        assertEquals(largeNegative, list.get(1));
    }

    @Test
    void testFlushAfterClose() throws IOException {
        list.add(42L);
        list.close();
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testAddAfterFileClose() throws Exception {
        Field fileField = LongList.class.getDeclaredField("file");
        fileField.setAccessible(true);
        RandomAccessFile raf = (RandomAccessFile) fileField.get(list);
        raf.close();

        assertThrows(UncheckedIOException.class, () -> list.add(42L));
        list = null;
    }
}
