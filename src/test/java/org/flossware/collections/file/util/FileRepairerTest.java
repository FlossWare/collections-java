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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileRepairerTest {
    @TempDir
    Path tempDir;

    private FileRepairer repairer;

    @BeforeEach
    void setUp() {
        repairer = new FileRepairer();
    }

    @Test
    void testRepairNonExistentFile() {
        File nonExistent = new File(tempDir.toFile(), "does-not-exist.bin");
        File output = new File(tempDir.toFile(), "output.bin");

        RepairResult result = repairer.repair(nonExistent, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("does not exist")));
    }

    @Test
    void testRepairToExistingOutput() throws IOException {
        File input = tempDir.resolve("input.bin").toFile();
        File output = tempDir.resolve("output.bin").toFile();

        // Create both files
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(input).build()) {
            list.add("test");
            list.flush();
        }
        output.createNewFile();

        RepairResult result = repairer.repair(input, output);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("already exists")));
    }

    @Test
    void testRepairValidFile() throws IOException {
        File corruptedFile = tempDir.resolve("corrupted.bin").toFile();
        File repairedFile = tempDir.resolve("repaired.bin").toFile();

        // Create a file with valid entries
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.flush();
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getEntriesRecovered());
        assertEquals(0, result.getEntriesLost());
        assertTrue(repairedFile.exists());

        // Verify repaired file is readable
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(repairedFile).build()) {
            assertEquals(3, list.size());
        }
    }

    @Test
    void testRepairTruncatedFile() throws IOException {
        File corruptedFile = tempDir.resolve("truncated.bin").toFile();
        File repairedFile = tempDir.resolve("repaired-truncated.bin").toFile();

        // Create file with entries then truncate it (disable checksums for easier truncation)
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(false)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.flush();
        }

        // Truncate to corrupt the last entry
        long originalSizeBeforeTruncate;
        long truncatedSize;
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            originalSizeBeforeTruncate = raf.length();
            truncatedSize = originalSizeBeforeTruncate - 10;
            raf.setLength(truncatedSize);
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertTrue(result.isSuccess());
        assertTrue(result.getEntriesRecovered() > 0);
        assertTrue(result.getEntriesLost() > 0);
        assertEquals(truncatedSize, result.getOriginalFileSize());
    }

    @Test
    void testRepairInvalidHeader() throws IOException {
        File corruptedFile = tempDir.resolve("bad-header.bin").toFile();
        File repairedFile = tempDir.resolve("repaired-header.bin").toFile();

        // Create file with invalid header
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            raf.writeInt(0xBADBAD);
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("header")));
    }

    @Test
    void testRepairFileWithoutChecksums() throws IOException {
        File corruptedFile = tempDir.resolve("no-checksums.bin").toFile();
        File repairedFile = tempDir.resolve("repaired-no-checksums.bin").toFile();

        // Create file without checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(false)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.flush();
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getEntriesRecovered());
    }

    @Test
    void testRepairResultToString() {
        RepairResult result = new RepairResult.Builder()
            .success(true)
            .entriesRecovered(10)
            .entriesLost(2)
            .originalFileSize(1000)
            .repairedFileSize(800)
            .addMessage("Repair complete")
            .build();

        String str = result.toString();

        assertNotNull(str);
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("entriesRecovered=10"));
        assertTrue(str.contains("entriesLost=2"));
    }

    @Test
    void testRepairResultGetters() {
        RepairResult result = new RepairResult.Builder()
            .success(true)
            .entriesRecovered(5)
            .entriesLost(1)
            .originalFileSize(500)
            .repairedFileSize(400)
            .addMessage("Test message")
            .build();

        assertTrue(result.isSuccess());
        assertEquals(5, result.getEntriesRecovered());
        assertEquals(1, result.getEntriesLost());
        assertEquals(500, result.getOriginalFileSize());
        assertEquals(400, result.getRepairedFileSize());
        assertEquals(1, result.getMessages().size());
    }

    @Test
    void testRepairCorruptedChecksum() throws IOException {
        File corruptedFile = tempDir.resolve("corrupted-crc.bin").toFile();
        File repairedFile = tempDir.resolve("repaired-crc.bin").toFile();

        // Create file with checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.flush();
        }

        // Corrupt a checksum
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            long pos = 64 + 4; // Header + first length field
            raf.seek(pos);
            long checksum = raf.readLong();
            raf.seek(pos);
            raf.writeLong(~checksum); // Flip checksum
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Some entries should be skipped due to corruption
        assertTrue(result.getEntriesLost() > 0);
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("checksum")));
    }

    @Test
    void testRepairCompletelyCorruptedFile() throws IOException {
        File corruptedFile = tempDir.resolve("all-bad.bin").toFile();
        File repairedFile = tempDir.resolve("repaired-all-bad.bin").toFile();

        // Create file then corrupt all entries
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("entry1");
            list.flush();
        }

        // Corrupt the length field to negative
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            raf.seek(64); // Skip header
            raf.writeInt(-999); // Invalid negative length
        }

        RepairResult result = repairer.repair(corruptedFile, repairedFile);

        // Should fail or recover 0 entries
        assertTrue(result.getEntriesRecovered() == 0);
        assertTrue(result.getMessages().stream().anyMatch(m ->
            m.contains("negative length") || m.contains("No entries")));
    }
}
