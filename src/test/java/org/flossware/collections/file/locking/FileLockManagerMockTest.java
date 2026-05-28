package org.flossware.collections.file.locking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.OverlappingFileLockException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileLockManagerMockTest {

    @Mock
    private RandomAccessFile mockFile;

    @Mock
    private FileChannel mockChannel;

    @Mock
    private FileLock mockLock;

    @Test
    void testAcquireLockReturnsNull() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean())).thenReturn(null);

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("Could not acquire file lock"));
    }

    @Test
    void testAcquireLockOverlappingException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenThrow(new OverlappingFileLockException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("already locked by this JVM"));
    }

    @Test
    void testAcquireLockInterruptedException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenThrow(new FileLockInterruptionException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted()); // Verify interrupt status was preserved
    }

    @Test
    void testAcquireLockNonReadableChannelException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenThrow(new NonReadableChannelException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, true)); // shared lock

        assertTrue(exception.getMessage().contains("not opened for reading"));
    }

    @Test
    void testUpgradeToExclusiveReturnsNull() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, true);

        // Mock the upgrade to return null
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenReturn(null);

        IOException exception = assertThrows(IOException.class, () ->
            lockManager.upgradeToExclusive());

        assertTrue(exception.getMessage().contains("Could not upgrade"));
    }

    @Test
    void testUpgradeToExclusiveOverlappingException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, true);

        // Mock the upgrade to throw OverlappingFileLockException
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenThrow(new OverlappingFileLockException());

        IOException exception = assertThrows(IOException.class, () ->
            lockManager.upgradeToExclusive());

        assertTrue(exception.getMessage().contains("already locked"));
    }

    @Test
    void testIsLockedWithInvalidLock() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenReturn(mockLock);
        when(mockLock.isValid()).thenReturn(true).thenReturn(false);

        FileLockManager lockManager = new FileLockManager(mockFile, false);
        assertTrue(lockManager.isLocked());

        // Now mock returns invalid
        assertFalse(lockManager.isLocked());
    }

    @Test
    void testCloseWithInvalidLock() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenReturn(mockLock);
        when(mockLock.isValid()).thenReturn(false);

        FileLockManager lockManager = new FileLockManager(mockFile, false);

        // Close should not throw even with invalid lock
        assertDoesNotThrow(() -> lockManager.close());
        verify(mockLock, never()).release();
    }

    @Test
    void testUpgradeWithNullLock() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(null); // First call returns null

        // This should fail during construction
        assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, true));
    }
}
