package org.flossware.collections.file.util;

import org.flossware.collections.file.format.EntryChecksum;
import org.flossware.collections.file.format.FileHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Utility to repair corrupted file-backed collection files by recovering valid entries.
 */
public class FileRepairer {
    private static final Logger logger = LoggerFactory.getLogger(FileRepairer.class);

    /**
     * Repairs a corrupted file by copying all valid entries to a new file.
     *
     * @param corruptedFile the corrupted file
     * @param repairedFile  the output file for recovered data
     * @return repair result with statistics
     */
    public RepairResult repair(File corruptedFile, File repairedFile) {
        RepairResult.Builder result = new RepairResult.Builder();

        if (!corruptedFile.exists()) {
            return result.addMessage("Source file does not exist: " + corruptedFile).build();
        }

        if (repairedFile.exists()) {
            return result.addMessage("Output file already exists: " + repairedFile).build();
        }

        long originalSize = corruptedFile.length();
        result.originalFileSize(originalSize);

        try (RandomAccessFile source = new RandomAccessFile(corruptedFile, "r");
             RandomAccessFile dest = new RandomAccessFile(repairedFile, "rw")) {

            // Read and copy header
            FileHeader header = FileHeader.read(source);
            if (header == null) {
                result.addMessage("Cannot repair: invalid or missing file header");
                return result.build();
            }

            header.write(dest);
            logger.info("Starting repair: source={}, size={}", corruptedFile, originalSize);

            boolean hasChecksums = header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED);
            long position = FileHeader.getHeaderSize();
            long entriesRecovered = 0;
            long entriesLost = 0;

            // Process each entry
            while (position + 4 <= originalSize) {
                source.seek(position);

                try {
                    int length = source.readInt();

                    // Validate length
                    if (length < 0 || length > 100 * 1024 * 1024) {
                        result.addMessage("Corrupt entry at position " + position + ": invalid length " + length + ", scanning ahead");
                        entriesLost++;
                        position++;
                        continue;
                    }

                    long checksumSize = hasChecksums ? 8 : 0;
                    long entrySize = 4 + checksumSize + length;

                    // Check bounds
                    if (position + entrySize > originalSize) {
                        result.addMessage("Truncated entry at position " + position + ": needed " +
                            entrySize + " bytes, only " + (originalSize - position) + " available");
                        entriesLost++;
                        position++;
                        continue;
                    }

                    boolean entryValid = true;

                    // Read checksum and data
                    long storedChecksum = 0;
                    if (hasChecksums) {
                        storedChecksum = source.readLong();
                    }

                    byte[] data = new byte[length];
                    source.readFully(data);

                    // Verify checksum if enabled
                    if (hasChecksums && !EntryChecksum.verifyChecksum(data, storedChecksum)) {
                        result.addMessage("Corrupt entry at position " + position + ": checksum mismatch, scanning ahead");
                        entriesLost++;
                        position++;
                        continue;
                    }

                    // Copy valid entry to output
                    if (entryValid) {
                        dest.writeInt(length);
                        if (hasChecksums) {
                            dest.writeLong(storedChecksum);
                        }
                        dest.write(data);
                        entriesRecovered++;
                    }

                    position += entrySize;

                } catch (IOException e) {
                    result.addMessage("I/O error at position " + position + ": " + e.getMessage() + ", scanning ahead");
                    entriesLost++;
                    position++;
                    continue;
                }
            }

            long repairedSize = dest.length();
            result.repairedFileSize(repairedSize);
            result.entriesRecovered(entriesRecovered);
            result.entriesLost(entriesLost);

            if (entriesRecovered > 0) {
                result.success(true);
                result.addMessage("Successfully recovered " + entriesRecovered + " entries");
                logger.info("Repair complete: recovered={}, lost={}, size={} -> {}",
                    entriesRecovered, entriesLost, originalSize, repairedSize);
            } else {
                result.addMessage("No entries could be recovered");
                logger.warn("Repair failed: no recoverable entries found");
            }

        } catch (IOException e) {
            result.addMessage("Repair failed: " + e.getMessage());
            logger.error("Repair failed with exception", e);
        }

        return result.build();
    }
}
