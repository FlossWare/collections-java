package org.flossware.jcollections.file.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileHeaderTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private RandomAccessFile raf;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test-header.bin").toFile();
        raf = new RandomAccessFile(testFile, "rw");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (raf != null) {
            raf.close();
        }
    }

    @Test
    void testCreateAndWriteHeader() throws IOException {
        FileHeader header = new FileHeader(FileHeader.VERSION_2, FileHeader.FLAG_CHECKSUMS_ENABLED | FileHeader.FLAG_MMAP_ENABLED);
        assertNotNull(header);
        header.write(raf);
        assertTrue(raf.length() > 0);
    }

    @Test
    void testReadHeader() throws IOException {
        FileHeader written = new FileHeader(FileHeader.VERSION_2, FileHeader.FLAG_CHECKSUMS_ENABLED);
        written.write(raf);

        raf.seek(0);
        FileHeader read = FileHeader.read(raf);

        assertNotNull(read);
        assertTrue(read.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED));
    }

    @Test
    void testReadNonExistentHeader() throws IOException {
        FileHeader header = FileHeader.read(raf);
        assertNull(header);
    }

    @Test
    void testHasFlag() {
        FileHeader header = new FileHeader(FileHeader.VERSION_2, FileHeader.FLAG_CHECKSUMS_ENABLED | FileHeader.FLAG_BTREE_INDEX);
        assertTrue(header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED));
        assertTrue(header.hasFlag(FileHeader.FLAG_BTREE_INDEX));
        assertFalse(header.hasFlag(FileHeader.FLAG_MMAP_ENABLED));
    }

    @Test
    void testGetVersion() {
        FileHeader header = new FileHeader(FileHeader.VERSION_2, 0);
        assertEquals(FileHeader.VERSION_2, header.getVersion());
    }

    @Test
    void testGetCreationTime() {
        FileHeader header = new FileHeader(FileHeader.VERSION_2, 0);
        assertTrue(header.getCreationTime() > 0);
    }

    @Test
    void testGetHeaderSize() {
        assertTrue(FileHeader.getHeaderSize() > 0);
    }

    @Test
    void testAllFlags() {
        int allFlags = FileHeader.FLAG_CHECKSUMS_ENABLED |
                       FileHeader.FLAG_MMAP_ENABLED |
                       FileHeader.FLAG_BTREE_INDEX |
                       FileHeader.FLAG_COMPRESSED;

        FileHeader header = new FileHeader(FileHeader.VERSION_2, allFlags);
        assertTrue(header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED));
        assertTrue(header.hasFlag(FileHeader.FLAG_MMAP_ENABLED));
        assertTrue(header.hasFlag(FileHeader.FLAG_BTREE_INDEX));
        assertTrue(header.hasFlag(FileHeader.FLAG_COMPRESSED));
    }

    @Test
    void testPersistenceRoundTrip() throws IOException {
        int flags = FileHeader.FLAG_CHECKSUMS_ENABLED | FileHeader.FLAG_BTREE_INDEX;
        FileHeader original = new FileHeader(FileHeader.VERSION_2, flags);
        original.write(raf);

        raf.seek(0);
        FileHeader restored = FileHeader.read(raf);

        assertEquals(original.getVersion(), restored.getVersion());
        assertEquals(original.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED),
                     restored.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED));
        assertEquals(original.hasFlag(FileHeader.FLAG_BTREE_INDEX),
                     restored.hasFlag(FileHeader.FLAG_BTREE_INDEX));
    }

    @Test
    void testVersion1() {
        FileHeader header = new FileHeader(FileHeader.VERSION_1, 0);
        assertEquals(FileHeader.VERSION_1, header.getVersion());
    }

    @Test
    void testGetFlags() {
        int flags = FileHeader.FLAG_CHECKSUMS_ENABLED | FileHeader.FLAG_MMAP_ENABLED;
        FileHeader header = new FileHeader(FileHeader.VERSION_2, flags);
        assertEquals(flags, header.getFlags());
    }
}
