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
 * Additional coverage tests for FileValidator targeting uncovered branches:
 * - Unreadable file (L35-36)
 * - Negative length entry (L64-68)
 * - Unusually large entry warning (L71-72)
 * - I/O error during entry read (L107-111)
 * - IOException opening file (L121-122)
 */
class FileValidatorCoverageTest {
    @TempDir
    Path tempDir;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator();
    }

    // =====================================================================
    // Negative length entry (L64-68)
    // =====================================================================

    @Test
    void testValidateNegativeLengthEntry() throws IOException {
        File file = tempDir.resolve("negative-length.bin").toFile();

        // Create a valid file first (no checksums for simpler corruption)
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .enableChecksums(false)
                .build()) {
            list.add("test");
            list.flush();
        }

        // Corrupt the length field to negative
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(FileHeader.getHeaderSize());
            raf.writeInt(-42); // Negative length
        }

        ValidationResult result = validator.validate(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("negative length")));
        assertTrue(result.getCorruptedEntries() > 0);
    }

    @Test
    void testValidateNegativeLengthEntryWithChecksums() throws IOException {
        File file = tempDir.resolve("negative-length-checksum.bin").toFile();

        // Create a valid file with checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .enableChecksums(true)
                .build()) {
            list.add("test data");
            list.flush();
        }

        // Corrupt the length field to negative
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(FileHeader.getHeaderSize());
            raf.writeInt(-100);
        }

        ValidationResult result = validator.validate(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("negative length")));
    }

    // =====================================================================
    // Unusually large entry warning (L71-72)
    // =====================================================================

    @Test
    void testValidateLargeEntryProducesWarning() throws IOException {
        File file = tempDir.resolve("large-entry-warning.bin").toFile();

        // Create a file with a very large entry (>100MB marker in length field)
        // We'll manually construct the file with a fake large length that still points within file bounds
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write valid header
            FileHeader header = new FileHeader(FileHeader.VERSION_2, 0); // No checksums
            header.write(raf);

            // Write an entry with length > 100_000_000
            int largeLength = 100_000_001;
            raf.writeInt(largeLength);

            // Write enough data to satisfy the length (pad with zeros)
            // This will make the entry valid but very large
            byte[] data = new byte[largeLength];
            raf.write(data);
        }

        ValidationResult result = validator.validate(file);

        // Should have a warning about large entry
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("unusually large")));
    }

    // =====================================================================
    // Truncated entry (entry size exceeds file bounds)
    // =====================================================================

    @Test
    void testValidateTruncatedEntryWithChecksums() throws IOException {
        File file = tempDir.resolve("truncated-checksum.bin").toFile();

        // Create a file with checksums then truncate
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .enableChecksums(true)
                .build()) {
            list.add("test data that will be truncated");
            list.flush();
        }

        // Truncate the file partway through the entry
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Keep header and length field but cut off data
            raf.setLength(FileHeader.getHeaderSize() + 4 + 8 + 3); // Header + length + checksum + 3 bytes of data
        }

        ValidationResult result = validator.validate(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e ->
            e.contains("exceeds file bounds") || e.contains("truncated")));
    }

    // =====================================================================
    // Unreadable file (L35-36)
    // =====================================================================

    @Test
    void testValidateUnreadableFile() throws IOException {
        File file = tempDir.resolve("unreadable.bin").toFile();
        file.createNewFile();

        // Try to make file unreadable
        boolean madeUnreadable = file.setReadable(false);

        if (madeUnreadable && !file.canRead()) {
            // Only test if we actually made it unreadable (may not work on all OS/users)
            ValidationResult result = validator.validate(file);
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("not readable")));
        }
        // If we couldn't make it unreadable (e.g., running as root), skip gracefully
        // Restore readability
        file.setReadable(true);
    }

    // =====================================================================
    // Multiple entries with mixed corruption
    // =====================================================================

    @Test
    void testValidateMultipleEntriesFirstCorrupted() throws IOException {
        File file = tempDir.resolve("first-corrupted.bin").toFile();

        // Create valid file with multiple entries
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .enableChecksums(false)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.flush();
        }

        // Corrupt the first entry's length to negative
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(FileHeader.getHeaderSize());
            raf.writeInt(-1);
        }

        ValidationResult result = validator.validate(file);

        assertFalse(result.isValid());
        // After negative length, validation should break
        assertTrue(result.getCorruptedEntries() > 0);
    }

    // =====================================================================
    // Valid file with checksums - all entries valid
    // =====================================================================

    @Test
    void testValidateValidFileWithChecksums() throws IOException {
        File file = tempDir.resolve("valid-checksums.bin").toFile();

        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .enableChecksums(true)
                .build()) {
            list.add("entry1");
            list.add("entry2");
            list.add("entry3");
            list.add("entry4");
            list.add("entry5");
            list.flush();
        }

        ValidationResult result = validator.validate(file);

        assertTrue(result.isValid());
        assertEquals(5, result.getTotalEntries());
        assertEquals(5, result.getValidEntries());
        assertEquals(0, result.getCorruptedEntries());
    }

    // =====================================================================
    // Empty file with valid header only
    // =====================================================================

    @Test
    void testValidateEmptyFileWithHeader() throws IOException {
        File file = tempDir.resolve("empty-with-header.bin").toFile();

        try (FileBackedList<String> list = new FileBackedList.Builder<String>(file)
                .build()) {
            // Don't add any entries
            list.flush();
        }

        ValidationResult result = validator.validate(file);

        assertTrue(result.isValid());
        assertEquals(0, result.getTotalEntries());
        assertEquals(0, result.getValidEntries());
    }

    // =====================================================================
    // File too small for header
    // =====================================================================

    @Test
    void testValidateFileTooSmallForHeader() throws IOException {
        File file = tempDir.resolve("tiny.bin").toFile();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // Write just a few bytes - too small for a valid header
            raf.writeInt(0x4A434F4C); // Magic bytes only
            // Header is 64 bytes, we only wrote 4
        }

        ValidationResult result = validator.validate(file);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e ->
            e.contains("header") || e.contains("Invalid")));
    }

    // =====================================================================
    // ValidationResult toString with errors and warnings
    // =====================================================================

    @Test
    void testValidationResultToStringWithErrors() {
        ValidationResult result = new ValidationResult.Builder()
            .addError("Error 1")
            .addError("Error 2")
            .totalEntries(5)
            .validEntries(3)
            .corruptedEntries(2)
            .build();

        String str = result.toString();

        assertTrue(str.contains("errors=["));
        assertTrue(str.contains("Error 1"));
        assertTrue(str.contains("Error 2"));
        assertTrue(str.contains("valid=false"));
    }

    @Test
    void testValidationResultToStringWithWarnings() {
        ValidationResult result = new ValidationResult.Builder()
            .addWarning("Warning 1")
            .addWarning("Warning 2")
            .totalEntries(10)
            .validEntries(10)
            .build();

        String str = result.toString();

        assertTrue(str.contains("warnings=["));
        assertTrue(str.contains("Warning 1"));
        assertTrue(str.contains("Warning 2"));
        assertTrue(str.contains("valid=true"));
    }

    @Test
    void testValidationResultToStringWithBothErrorsAndWarnings() {
        ValidationResult result = new ValidationResult.Builder()
            .addError("Test error")
            .addWarning("Test warning")
            .totalEntries(3)
            .validEntries(2)
            .corruptedEntries(1)
            .build();

        String str = result.toString();

        assertTrue(str.contains("errors=["));
        assertTrue(str.contains("warnings=["));
        assertTrue(str.contains("Test error"));
        assertTrue(str.contains("Test warning"));
    }
}
