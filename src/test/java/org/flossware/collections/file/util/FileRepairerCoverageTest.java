package org.flossware.collections.file.util;

import org.flossware.collections.file.FileBackedList;
import org.flossware.collections.file.format.FileHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage tests for FileRepairer targeting uncovered branches:
 * - Scan-ahead behavior for corrupt entries mid-file with valid entries after
 * - Large invalid length (>100MB) corruption
 * - Checksum mismatch with scan-ahead
 * - Multiple corrupt entries interleaved with valid ones
 * - Repair of file with no recoverable entries
 */
class FileRepairerCoverageTest {
    @TempDir
    Path tempDir;

    private FileRepairer repairer;

    @BeforeEach
    void setUp() {
        repairer = new FileRepairer();
    }

    // =====================================================================
    // Scan-ahead: corrupt entry followed by valid entry
    // =====================================================================

    @Test
    void testRepairCorruptEntryMidFileWithScanAhead() throws IOException {
        File corruptedFile = tempDir.resolve("scan-ahead.bin").toFile();
        File repairedFile = tempDir.resolve("scan-ahead-repaired.bin").toFile();

        // Create a valid file without checksums (simpler to manually corrupt)
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(false)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.flush();
        }

        // Corrupt the second entry's length to a large invalid value
        // Read the file structure first
        long secondEntryOffset;
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "r")) {
            raf.seek(FileHeader.getHeaderSize());
            int firstLen = raf.readInt();
            secondEntryOffset = FileHeader.getHeaderSize() + 4 + firstLen;
        }

        // Make the second entry's length absurdly large (>100MB)
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            raf.seek(secondEntryOffset);
            raf.writeInt(200_000_000); // Invalid: >100MB
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Should recover at least the first entry and possibly the third via scan-ahead
        assertTrue(result.getEntriesRecovered() >= 1);
        assertTrue(result.getEntriesLost() > 0);
        assertTrue(result.getMessages().stream().anyMatch(m ->
            m.contains("invalid length") || m.contains("scanning ahead")));
    }

    // =====================================================================
    // Negative length corruption
    // =====================================================================

    @Test
    void testRepairNegativeLengthCorruption() throws IOException {
        File corruptedFile = tempDir.resolve("neg-length.bin").toFile();
        File repairedFile = tempDir.resolve("neg-length-repaired.bin").toFile();

        // Create a valid file without checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(false)
                .build()) {
            list.add("good-entry-1");
            list.add("good-entry-2");
            list.flush();
        }

        // Corrupt the first entry's length to negative
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            raf.seek(FileHeader.getHeaderSize());
            raf.writeInt(-5); // Negative length
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Should report corruption and scan ahead
        assertTrue(result.getEntriesLost() > 0);
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("invalid length")));
    }

    // =====================================================================
    // Checksum mismatch with scan-ahead (L96-102)
    // =====================================================================

    @Test
    void testRepairChecksumMismatchWithScanAhead() throws IOException {
        File corruptedFile = tempDir.resolve("checksum-scan.bin").toFile();
        File repairedFile = tempDir.resolve("checksum-scan-repaired.bin").toFile();

        // Create a valid file WITH checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("entry-alpha");
            list.add("entry-beta");
            list.add("entry-gamma");
            list.flush();
        }

        // Corrupt the data of the second entry (not the checksum) to cause mismatch
        long secondEntryDataOffset;
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "r")) {
            raf.seek(FileHeader.getHeaderSize());
            int firstLen = raf.readInt();
            // First entry: 4 (length) + 8 (checksum) + firstLen (data)
            long firstEntrySize = 4 + 8 + firstLen;
            secondEntryDataOffset = FileHeader.getHeaderSize() + firstEntrySize;
        }

        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            // Skip to second entry's data (after length and checksum)
            raf.seek(secondEntryDataOffset + 4 + 8);
            raf.write(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF});
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Should lose the corrupted entry but recover others via scan-ahead
        assertTrue(result.getEntriesLost() > 0);
        assertTrue(result.getMessages().stream().anyMatch(m ->
            m.contains("checksum mismatch") || m.contains("scanning ahead")));
    }

    // =====================================================================
    // Truncated entry at end of file
    // =====================================================================

    @Test
    void testRepairTruncatedEntryAtEnd() throws IOException {
        File corruptedFile = tempDir.resolve("truncated-end.bin").toFile();
        File repairedFile = tempDir.resolve("truncated-end-repaired.bin").toFile();

        // Create valid file
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(false)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("a longer entry that will be truncated");
            list.flush();
        }

        // Truncate the last entry partially
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            long originalSize = raf.length();
            raf.setLength(originalSize - 15); // Cut off 15 bytes from end
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertTrue(result.isSuccess());
        assertTrue(result.getEntriesRecovered() >= 2);
        assertTrue(result.getEntriesLost() > 0);
        assertTrue(result.getMessages().stream().anyMatch(m ->
            m.contains("Truncated entry") || m.contains("scanning ahead")));
    }

    // =====================================================================
    // File with only header and no entries
    // =====================================================================

    @Test
    void testRepairFileWithOnlyHeader() throws IOException {
        File corruptedFile = tempDir.resolve("header-only.bin").toFile();
        File repairedFile = tempDir.resolve("header-only-repaired.bin").toFile();

        // Create an empty file (just header)
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .build()) {
            list.flush();
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // No entries to recover
        assertFalse(result.isSuccess());
        assertEquals(0, result.getEntriesRecovered());
        assertTrue(result.getMessages().stream().anyMatch(m ->
            m.contains("No entries")));
    }

    // =====================================================================
    // Repair with checksums - valid entries
    // =====================================================================

    @Test
    void testRepairValidFileWithChecksums() throws IOException {
        File corruptedFile = tempDir.resolve("valid-checksums.bin").toFile();
        File repairedFile = tempDir.resolve("valid-checksums-repaired.bin").toFile();

        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("alpha");
            list.add("beta");
            list.add("gamma");
            list.add("delta");
            list.flush();
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertTrue(result.isSuccess());
        assertEquals(4, result.getEntriesRecovered());
        assertEquals(0, result.getEntriesLost());
        assertTrue(result.getRepairedFileSize() > 0);
        assertTrue(result.getOriginalFileSize() > 0);
    }

    // =====================================================================
    // Completely garbage data after header
    // =====================================================================

    @Test
    void testRepairAllGarbageAfterHeader() throws IOException {
        File corruptedFile = tempDir.resolve("all-garbage.bin").toFile();
        File repairedFile = tempDir.resolve("all-garbage-repaired.bin").toFile();

        // Create a file with valid header but all garbage data after it
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            FileHeader header = new FileHeader(FileHeader.VERSION_2, 0);
            header.write(raf);

            // Write garbage after header
            byte[] garbage = new byte[200];
            for (int i = 0; i < garbage.length; i++) {
                garbage[i] = (byte) (i % 256);
            }
            raf.write(garbage);
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Everything after the header is garbage, so no entries recovered
        // The repairer scans ahead byte by byte through garbage
        assertEquals(0, result.getEntriesRecovered());
    }

    // =====================================================================
    // Multiple repair result message accumulation
    // =====================================================================

    @Test
    void testRepairResultWithMultipleMessages() {
        RepairResult result = new RepairResult.Builder()
            .addMessage("Message 1")
            .addMessage("Message 2")
            .addMessage("Message 3")
            .success(false)
            .build();

        assertEquals(3, result.getMessages().size());
        assertFalse(result.isSuccess());
    }
}
