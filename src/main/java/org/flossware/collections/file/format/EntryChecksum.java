package org.flossware.collections.file.format;

import java.util.zip.CRC32;

public class EntryChecksum {

    public static long calculateChecksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public static boolean verifyChecksum(byte[] data, long expectedChecksum) {
        return calculateChecksum(data) == expectedChecksum;
    }
}
