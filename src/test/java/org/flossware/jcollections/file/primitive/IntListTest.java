package org.flossware.jcollections.file.primitive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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
}
