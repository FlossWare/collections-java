package org.flossware.collections.file.format;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for FileHeader targeting uncovered paths:
 * - L74: return null when magic bytes don't match (file >= 64 bytes)
 * - L88: throw IOException when header checksum doesn't match
 */
class FileHeaderCoverageTest {
    @TempDir
    Path tempDir;

    @Test
    void testReadHeaderWithWrongMagicBytesFullSizeFile() throws IOException {
        // Create a 64-byte file with wrong magic bytes
        // This tests L74: return null when magic != MAGIC_BYTES
        File file = tempDir.resolve("wrong-magic-full.bin").toFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write 64 bytes with wrong magic
            byte[] data = new byte[64];
            data[0] = (byte) 0xDE;
            data[1] = (byte) 0xAD;
            data[2] = (byte) 0xBE;
            data[3] = (byte) 0xEF;
            raf.write(data);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileHeader header = FileHeader.read(raf);
            assertNull(header); // Should return null due to wrong magic bytes
        }
    }

    @Test
    void testReadHeaderWithWrongMagicBytesLargerFile() throws IOException {
        // Create a file larger than 64 bytes with wrong magic bytes
        File file = tempDir.resolve("wrong-magic-large.bin").toFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] data = new byte[256];
            // Wrong magic bytes at start
            data[0] = (byte) 0x00;
            data[1] = (byte) 0x00;
            data[2] = (byte) 0x00;
            data[3] = (byte) 0x01;
            raf.write(data);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileHeader header = FileHeader.read(raf);
            assertNull(header);
        }
    }

    @Test
    void testReadHeaderWithCorruptedChecksum() throws IOException {
        // Create a valid header then corrupt the checksum
        // This tests L88: throw IOException when checksum doesn't match
        File file = tempDir.resolve("corrupt-checksum.bin").toFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write a valid header first
            FileHeader header = new FileHeader(FileHeader.VERSION_2, FileHeader.FLAG_CHECKSUMS_ENABLED);
            header.write(raf);

            // Now corrupt the header checksum (last 8 bytes of the 64-byte header)
            raf.seek(FileHeader.getHeaderSize() - 8);
            long checksum = raf.readLong();
            raf.seek(FileHeader.getHeaderSize() - 8);
            raf.writeLong(~checksum); // Flip all bits
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            IOException exception = assertThrows(IOException.class, () ->
                FileHeader.read(raf));

            assertTrue(exception.getMessage().contains("checksum mismatch"));
        }
    }

    @Test
    void testReadHeaderWithCorruptedData() throws IOException {
        // Corrupt the header data (not the checksum) to cause checksum mismatch
        File file = tempDir.resolve("corrupt-data.bin").toFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            FileHeader header = new FileHeader(FileHeader.VERSION_2, 0);
            header.write(raf);

            // Corrupt the version field (bytes 4-7)
            raf.seek(4);
            raf.writeInt(999);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            IOException exception = assertThrows(IOException.class, () ->
                FileHeader.read(raf));

            assertTrue(exception.getMessage().contains("checksum mismatch"));
        }
    }
}
