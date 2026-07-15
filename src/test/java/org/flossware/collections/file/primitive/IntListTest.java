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

import org.flossware.collections.file.format.FileHeader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void testFsyncEnabledAddAndGet() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-intlist.bin").toFile();
        try (IntList fsyncList = new IntList(fsyncFile, true)) {
            fsyncList.add(10);
            fsyncList.add(20);
            fsyncList.add(30);

            assertEquals(3, fsyncList.size());
            assertEquals(10, fsyncList.get(0));
            assertEquals(20, fsyncList.get(1));
            assertEquals(30, fsyncList.get(2));
        }
    }

    @Test
    void testFsyncDefaultConstructor() throws IOException {
        // Default constructor should have fsync disabled (no FLAG_FSYNC_ENABLED)
        File defaultFile = tempDir.resolve("fsync-default.bin").toFile();
        try (IntList defaultList = new IntList(defaultFile)) {
            defaultList.add(42);
            assertEquals(1, defaultList.size());
        }
    }

    @Test
    void testFsyncEnabledPersistence() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-persist.bin").toFile();
        try (IntList fsyncList = new IntList(fsyncFile, true)) {
            fsyncList.add(100);
            fsyncList.add(200);
            fsyncList.flush();
        }

        try (IntList reopened = new IntList(fsyncFile)) {
            assertEquals(2, reopened.size());
            assertEquals(100, reopened.get(0));
            assertEquals(200, reopened.get(1));
        }
    }

    @Test
    void testFsyncEnabledFlush() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-flush.bin").toFile();
        try (IntList fsyncList = new IntList(fsyncFile, true)) {
            fsyncList.add(42);
            assertDoesNotThrow(() -> fsyncList.flush());
        }
    }

    @Test
    void testFsyncEnabledHeaderFlag() throws IOException {
        File fsyncFile = tempDir.resolve("fsync-flag.bin").toFile();
        try (IntList fsyncList = new IntList(fsyncFile, true)) {
            fsyncList.add(1);
            fsyncList.flush();
        }

        // Reopen and verify the flag is in the header
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(fsyncFile, "r");
        FileHeader header = FileHeader.read(raf);
        raf.close();
        assertTrue(header.hasFlag(FileHeader.FLAG_FSYNC_ENABLED));
        assertTrue(header.hasFlag(FileHeader.FLAG_MMAP_ENABLED));
    }

    @Test
    void testFsyncDisabledHeaderFlag() throws IOException {
        File noFsyncFile = tempDir.resolve("no-fsync-flag.bin").toFile();
        try (IntList noFsyncList = new IntList(noFsyncFile, false)) {
            noFsyncList.add(1);
            noFsyncList.flush();
        }

        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(noFsyncFile, "r");
        FileHeader header = FileHeader.read(raf);
        raf.close();
        assertFalse(header.hasFlag(FileHeader.FLAG_FSYNC_ENABLED));
        assertTrue(header.hasFlag(FileHeader.FLAG_MMAP_ENABLED));
    }

    @Test
    void testFlushAfterClose() throws IOException {
        list.add(42);
        list.close();
        assertDoesNotThrow(() -> list.flush());
    }

    @Test
    void testAddAfterFileClose() throws Exception {
        Field fileField = IntList.class.getDeclaredField("file");
        fileField.setAccessible(true);
        RandomAccessFile raf = (RandomAccessFile) fileField.get(list);
        raf.close();

        assertThrows(UncheckedIOException.class, () -> list.add(42));
        list = null;
    }
}
