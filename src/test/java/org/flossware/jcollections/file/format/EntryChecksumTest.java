package org.flossware.jcollections.file.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryChecksumTest {

    @Test
    void testCalculateChecksum() {
        byte[] data = "test data".getBytes();
        long checksum = EntryChecksum.calculateChecksum(data);
        assertTrue(checksum > 0);
    }

    @Test
    void testCalculateChecksumConsistency() {
        byte[] data = "consistent data".getBytes();
        long checksum1 = EntryChecksum.calculateChecksum(data);
        long checksum2 = EntryChecksum.calculateChecksum(data);
        assertEquals(checksum1, checksum2);
    }

    @Test
    void testDifferentDataDifferentChecksum() {
        byte[] data1 = "data one".getBytes();
        byte[] data2 = "data two".getBytes();
        long checksum1 = EntryChecksum.calculateChecksum(data1);
        long checksum2 = EntryChecksum.calculateChecksum(data2);
        assertNotEquals(checksum1, checksum2);
    }

    @Test
    void testVerifyChecksum() {
        byte[] data = "verify this".getBytes();
        long checksum = EntryChecksum.calculateChecksum(data);
        assertTrue(EntryChecksum.verifyChecksum(data, checksum));
        assertFalse(EntryChecksum.verifyChecksum(data, checksum + 1));
    }

    @Test
    void testEmptyData() {
        byte[] empty = new byte[0];
        long checksum = EntryChecksum.calculateChecksum(empty);
        assertTrue(checksum >= 0);
    }

    @Test
    void testLargeData() {
        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        long checksum = EntryChecksum.calculateChecksum(largeData);
        assertTrue(checksum > 0);
    }

    @Test
    void testSingleByte() {
        byte[] singleByte = {42};
        long checksum = EntryChecksum.calculateChecksum(singleByte);
        assertTrue(checksum >= 0);
    }

    @Test
    void testAllZeros() {
        byte[] zeros = new byte[100];
        long checksum = EntryChecksum.calculateChecksum(zeros);
        assertTrue(checksum >= 0);
    }

    @Test
    void testAllOnes() {
        byte[] ones = new byte[100];
        for (int i = 0; i < ones.length; i++) {
            ones[i] = (byte) 0xFF;
        }
        long checksum = EntryChecksum.calculateChecksum(ones);
        assertTrue(checksum >= 0);
    }
}
