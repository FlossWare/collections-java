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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoubleListTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private DoubleList list;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.bin").toFile();
        list = new DoubleList(testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (list != null) {
            list.close();
        }
    }

    @Test
    void testAddAndGet() {
        list.add(10.5);
        list.add(20.25);
        list.add(30.125);

        assertEquals(3, list.size());
        assertEquals(10.5, list.get(0));
        assertEquals(20.25, list.get(1));
        assertEquals(30.125, list.get(2));
    }

    @Test
    void testLargeDataset() {
        for (int i = 0; i < 10000; i++) {
            list.add(i * 2.5);
        }

        assertEquals(10000, list.size());
        assertEquals(0.0, list.get(0));
        assertEquals(9999 * 2.5, list.get(9999));
    }

    @Test
    void testPersistence() throws IOException {
        list.add(100.1);
        list.add(200.2);
        list.flush();
        list.close();

        DoubleList newList = new DoubleList(testFile);
        assertEquals(2, newList.size());
        assertEquals(100.1, newList.get(0));
        assertEquals(200.2, newList.get(1));
        newList.close();
    }

    @Test
    void testFlush() {
        list.add(42.0);
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testIndexOutOfBounds() {
        list.add(10.0);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    void testLargeCapacity() throws IOException {
        for (int i = 0; i < 10000; i++) {
            list.add((double) i);
        }
        assertEquals(10000, list.size());
        assertEquals(5000.0, list.get(5000));
    }

    @Test
    void testMultipleFlush() throws IOException {
        list.add(1.0);
        list.add(2.0);
        list.flush();
        list.add(3.0);
        list.add(4.0);
        list.flush();
        assertEquals(4, list.size());
    }

    @Test
    void testNegativeNumbers() throws IOException {
        list.add(-100.5);
        list.add(-1.0);
        list.add(0.0);
        list.add(1.0);
        list.add(100.5);
        assertEquals(-100.5, list.get(0));
        assertEquals(100.5, list.get(4));
    }

    @Test
    void testCloseAndReopen() throws IOException {
        list.add(111.11);
        list.add(222.22);
        list.close();

        DoubleList reopened = new DoubleList(testFile);
        assertEquals(2, reopened.size());
        assertEquals(111.11, reopened.get(0));
        assertEquals(222.22, reopened.get(1));
        reopened.close();
    }

    @Test
    void testMultipleClose() throws IOException {
        list.add(42.0);
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
        list.add(0.0);
        assertEquals(0.0, list.get(0));
    }

    @Test
    void testMaxAndMinDouble() {
        list.add(Double.MAX_VALUE);
        list.add(Double.MIN_VALUE);
        assertEquals(Double.MAX_VALUE, list.get(0));
        assertEquals(Double.MIN_VALUE, list.get(1));
    }

    @Test
    void testSpecialValues() {
        list.add(Double.NaN);
        list.add(Double.POSITIVE_INFINITY);
        list.add(Double.NEGATIVE_INFINITY);

        assertTrue(Double.isNaN(list.get(0)));
        assertEquals(Double.POSITIVE_INFINITY, list.get(1));
        assertEquals(Double.NEGATIVE_INFINITY, list.get(2));
    }

    @Test
    void testPrecision() {
        double precise = 3.141592653589793;
        list.add(precise);
        assertEquals(precise, list.get(0));
    }

    @Test
    void testFlushAfterClose() throws IOException {
        list.add(42.0);
        list.close();
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testAddAfterFileClose() throws Exception {
        Field fileField = DoubleList.class.getDeclaredField("file");
        fileField.setAccessible(true);
        RandomAccessFile raf = (RandomAccessFile) fileField.get(list);
        raf.close();

        assertThrows(UncheckedIOException.class, () -> list.add(42.0));
        list = null;
    }
}
