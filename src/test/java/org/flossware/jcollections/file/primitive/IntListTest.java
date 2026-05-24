package org.flossware.jcollections.file.primitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntListTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private IntList list;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test.bin").toFile();
        list = new IntList(testFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (list != null) {
            list.close();
        }
    }

    @Test
    void testAddAndGet() {
        list.add(10);
        list.add(20);
        list.add(30);

        assertEquals(3, list.size());
        assertEquals(10, list.get(0));
        assertEquals(20, list.get(1));
        assertEquals(30, list.get(2));
    }

    @Test
    void testLargeDataset() {
        for (int i = 0; i < 10000; i++) {
            list.add(i * 2);
        }

        assertEquals(10000, list.size());
        assertEquals(0, list.get(0));
        assertEquals(9999 * 2, list.get(9999));
    }

    @Test
    void testPersistence() throws IOException {
        list.add(100);
        list.add(200);
        list.flush();
        list.close();

        IntList newList = new IntList(testFile);
        assertEquals(2, newList.size());
        assertEquals(100, newList.get(0));
        assertEquals(200, newList.get(1));
        newList.close();
    }

    @Test
    void testFlush() {
        list.add(42);
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testIndexOutOfBounds() {
        list.add(10);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
    }

    @Test
    void testLargeCapacity() throws IOException {
        for (int i = 0; i < 10000; i++) {
            list.add(i);
        }
        assertEquals(10000, list.size());
        assertEquals(5000, list.get(5000));
    }

    @Test
    void testMultipleFlush() throws IOException {
        list.add(1);
        list.add(2);
        list.flush();
        list.add(3);
        list.add(4);
        list.flush();
        assertEquals(4, list.size());
    }

    @Test
    void testNegativeNumbers() throws IOException {
        list.add(-100);
        list.add(-1);
        list.add(0);
        list.add(1);
        list.add(100);
        assertEquals(-100, list.get(0));
        assertEquals(100, list.get(4));
    }

    @Test
    void testCloseAndReopen() throws IOException {
        list.add(111);
        list.add(222);
        list.close();

        IntList reopened = new IntList(testFile);
        assertEquals(2, reopened.size());
        assertEquals(111, reopened.get(0));
        assertEquals(222, reopened.get(1));
        reopened.close();
    }

    @Test
    void testMultipleClose() throws IOException {
        list.add(42);
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
        list.add(0);
        assertEquals(0, list.get(0));
    }

    @Test
    void testMaxAndMinInt() {
        list.add(Integer.MAX_VALUE);
        list.add(Integer.MIN_VALUE);
        assertEquals(Integer.MAX_VALUE, list.get(0));
        assertEquals(Integer.MIN_VALUE, list.get(1));
    }
}
