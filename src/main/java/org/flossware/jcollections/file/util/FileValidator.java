package org.flossware.jcollections.file.util;

import org.flossware.jcollections.file.format.EntryChecksum;
import org.flossware.jcollections.file.format.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Utility to validate the integrity of file-backed collection files.
 */
public class FileValidator {
    private static final Logger logger = LoggerFactory.getLogger(FileValidator.class);

    /**
     * Validates a file-backed collection file.
     *
     * @param file the file to validate
     * @return validation result with errors, warnings, and statistics
     */
    public ValidationResult validate(File file) {
        ValidationResult.Builder result = new ValidationResult.Builder();

        if (!file.exists()) {
            return result.addError("File does not exist: " + file).build();
        }

        if (!file.isFile()) {
            return result.addError("Path is not a file: " + file).build();
        }

        if (!file.canRead()) {
            return result.addError("File is not readable: " + file).build();
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Validate header
            FileHeader header = FileHeader.read(raf);
            if (header == null) {
                return result.addError("Invalid or missing file header").build();
            }

            logger.debug("Validating file: {}, version={}, size={}", file, header.getVersion(), file.length());

            boolean hasChecksums = header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED);
            long fileSize = raf.length();
            long position = FileHeader.getHeaderSize();
            long validEntries = 0;
            long corruptedEntries = 0;
            long totalEntries = 0;

            // Validate each entry
            while (position + 4 <= fileSize) {
                totalEntries++;
                raf.seek(position);

                try {
                    int length = raf.readInt();

                    // Validate length
                    if (length < 0) {
                        result.addError("Entry " + totalEntries + " at position " + position +
                            ": invalid negative length " + length);
                        corruptedEntries++;
                        break;  // Cannot continue after invalid length
                    }

                    if (length > 100_000_000) {
                        result.addWarning("Entry " + totalEntries + " at position " + position +
                            ": unusually large entry (" + length + " bytes)");
                    }

                    long checksumSize = hasChecksums ? 8 : 0;
                    long entrySize = 4 + checksumSize + length;

                    // Check bounds
                    if (position + entrySize > fileSize) {
                        result.addError("Entry " + totalEntries + " at position " + position +
                            ": entry size " + entrySize + " bytes exceeds file bounds (truncated file?)");
                        corruptedEntries++;
                        break;
                    }

                    // Verify checksum if enabled
                    if (hasChecksums) {
                        long storedChecksum = raf.readLong();
                        byte[] data = new byte[length];
                        raf.readFully(data);

                        if (!EntryChecksum.verifyChecksum(data, storedChecksum)) {
                            result.addError("Entry " + totalEntries + " at position " + position +
                                ": checksum mismatch (data corruption detected)");
                            corruptedEntries++;
                        } else {
                            validEntries++;
                        }
                    } else {
                        // No checksums - assume valid
                        validEntries++;
                    }

                    position += entrySize;

                } catch (IOException e) {
                    result.addError("Entry " + totalEntries + " at position " + position +
                        ": I/O error: " + e.getMessage());
                    corruptedEntries++;
                    break;
                }
            }

            result.totalEntries(totalEntries);
            result.validEntries(validEntries);
            result.corruptedEntries(corruptedEntries);

            logger.info("Validation complete: total={}, valid={}, corrupted={}", totalEntries, validEntries, corruptedEntries);

        } catch (IOException e) {
            result.addError("Failed to open file: " + e.getMessage());
        }

        return result.build();
    }
}
