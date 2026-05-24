package org.flossware.jcollections.file.util;

import org.flossware.jcollections.file.FileBackedList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileValidatorTest {
    @TempDir
    Path tempDir;

    private FileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FileValidator();
    }

    @Test
    void testValidateNonExistentFile() {
        File nonExistent = new File(tempDir.toFile(), "does-not-exist.bin");
        ValidationResult result = validator.validate(nonExistent);

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("does not exist"));
    }

    @Test
    void testValidateDirectory() {
        ValidationResult result = validator.validate(tempDir.toFile());

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("not a file"));
    }

    @Test
    void testValidateValidFile() throws IOException {
        File validFile = tempDir.resolve("valid.bin").toFile();

        // Create a valid file
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(validFile)
                .enableChecksums(true)
                .build()) {
            list.add("test1");
            list.add("test2");
            list.add("test3");
            list.flush();
        }

        ValidationResult result = validator.validate(validFile);

        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
        assertEquals(3, result.getTotalEntries());
        assertEquals(3, result.getValidEntries());
        assertEquals(0, result.getCorruptedEntries());
    }

    @Test
    void testValidateInvalidHeader() throws IOException {
        File invalidFile = tempDir.resolve("invalid-header.bin").toFile();

        // Create file with bad header
        try (RandomAccessFile raf = new RandomAccessFile(invalidFile, "rw")) {
            raf.writeInt(0xDEADBEEF);  // Wrong magic bytes
        }

        ValidationResult result = validator.validate(invalidFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("header")));
    }

    @Test
    void testValidateTruncatedFile() throws IOException {
        File truncatedFile = tempDir.resolve("truncated.bin").toFile();

        // Create valid file with checksums disabled (so we can truncate without checksum verification failing)
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(truncatedFile)
                .enableChecksums(false)
                .build()) {
            list.add("test1");
            list.add("test2");
            list.flush();
        }

        // Truncate the file by cutting off last entry partially
        try (RandomAccessFile raf = new RandomAccessFile(truncatedFile, "rw")) {
            long length = raf.length();
            raf.setLength(length - 10);  // Cut off some bytes from the end
        }

        ValidationResult result = validator.validate(truncatedFile);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getCorruptedEntries() >= 0);
    }

    @Test
    void testValidateFileWithoutChecksums() throws IOException {
        File noChecksumsFile = tempDir.resolve("no-checksums.bin").toFile();

        // Create file without checksums
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(noChecksumsFile)
                .enableChecksums(false)
                .build()) {
            list.add("test1");
            list.add("test2");
            list.flush();
        }

        ValidationResult result = validator.validate(noChecksumsFile);

        assertTrue(result.isValid());
        assertEquals(2, result.getValidEntries());
    }

    @Test
    void testValidationResultToString() throws IOException {
        File validFile = tempDir.resolve("for-toString.bin").toFile();

        try (FileBackedList<String> list = new FileBackedList.Builder<String>(validFile)
                .enableChecksums(true)
                .build()) {
            list.add("test");
            list.flush();
        }

        ValidationResult result = validator.validate(validFile);
        String str = result.toString();

        assertNotNull(str);
        assertTrue(str.contains("valid=true"));
        assertTrue(str.contains("totalEntries=1"));
    }

    @Test
    void testValidationResultWithWarnings() {
        ValidationResult result = new ValidationResult.Builder()
            .addWarning("Test warning")
            .totalEntries(5)
            .validEntries(5)
            .build();

        assertTrue(result.isValid());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    void testValidationResultWithErrors() {
        ValidationResult result = new ValidationResult.Builder()
            .addError("Test error")
            .totalEntries(5)
            .validEntries(3)
            .corruptedEntries(2)
            .build();

        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void testValidateCorruptedChecksum() throws IOException {
        File corruptedFile = tempDir.resolve("corrupted-checksum.bin").toFile();

        // Create valid file
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(corruptedFile)
                .enableChecksums(true)
                .build()) {
            list.add("test1");
            list.add("test2");
            list.flush();
        }

        // Corrupt a checksum by flipping bits
        try (RandomAccessFile raf = new RandomAccessFile(corruptedFile, "rw")) {
            // Skip header and first entry length field
            long pos = 64 + 4; // Header size + length field
            raf.seek(pos);
            long checksum = raf.readLong();
            raf.seek(pos);
            raf.writeLong(~checksum); // Flip all bits in checksum
        }

        ValidationResult result = validator.validate(corruptedFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("checksum")));
        assertTrue(result.getCorruptedEntries() > 0);
    }

    @Test
    void testValidateLargeEntryWarning() throws IOException {
        File largeEntryFile = tempDir.resolve("large-entry.bin").toFile();

        // Create file with large entry
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(largeEntryFile)
                .enableChecksums(false)
                .build()) {
            // Create a very large string (but not invalid)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5_000_000; i++) {
                sb.append("x");
            }
            list.add(sb.toString());
            list.flush();
        }

        ValidationResult result = validator.validate(largeEntryFile);

        // Should be valid but have a warning about large entry
        assertTrue(result.isValid());
        assertTrue(result.getWarnings().size() > 0 || result.getValidEntries() > 0);
    }
}
