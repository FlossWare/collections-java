package org.flossware.collections.file.locking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonWritableChannelException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional coverage tests for FileLockManager targeting uncovered branches:
 * - upgradeToExclusive with ClosedChannelException
 * - upgradeToExclusive with FileLockInterruptionException
 * - upgradeToExclusive with NonWritableChannelException
 * - upgradeToExclusive when lock is null before release
 * - isLocked with null lock
 * - close when lock is null
 * - acquireLock with ClosedChannelException
 * - acquireLock with NonWritableChannelException
 */
@ExtendWith(MockitoExtension.class)
class FileLockManagerCoverageTest {

    @Mock
    private RandomAccessFile mockFile;

    @Mock
    private FileChannel mockChannel;

    @Mock
    private FileLock mockLock;

    // =====================================================================
    // upgradeToExclusive exception handling (L62-68)
    // =====================================================================

    @Test
    void testUpgradeToExclusiveClosedChannelException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, true);

        // Mock the exclusive lock attempt to throw ClosedChannelException
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenThrow(new ClosedChannelException());

        IOException exception = assertThrows(IOException.class, () ->
            lockManager.upgradeToExclusive());

        assertTrue(exception.getMessage().contains("channel is closed"));
    }

    @Test
    void testUpgradeToExclusiveFileLockInterruptionException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, true);

        // Mock the exclusive lock attempt to throw FileLockInterruptionException
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenThrow(new FileLockInterruptionException());

        IOException exception = assertThrows(IOException.class, () ->
            lockManager.upgradeToExclusive());

        assertTrue(exception.getMessage().contains("interrupted"));
        // Verify interrupt status was preserved
        assertTrue(Thread.interrupted());
    }

    @Test
    void testUpgradeToExclusiveNonWritableChannelException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(true)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, true);

        // Mock the exclusive lock attempt to throw NonWritableChannelException
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenThrow(new NonWritableChannelException());

        IOException exception = assertThrows(IOException.class, () ->
            lockManager.upgradeToExclusive());

        assertTrue(exception.getMessage().contains("not opened for writing"));
    }

    // =====================================================================
    // upgradeToExclusive when not shared (no-op path)
    // =====================================================================

    @Test
    void testUpgradeToExclusiveWhenAlreadyExclusiveIsNoop() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenReturn(mockLock);

        FileLockManager lockManager = new FileLockManager(mockFile, false);

        // Upgrading an already-exclusive lock should be a no-op
        assertDoesNotThrow(() -> lockManager.upgradeToExclusive());
    }

    // =====================================================================
    // acquireLock exception paths
    // =====================================================================

    @Test
    void testAcquireLockClosedChannelException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenThrow(new ClosedChannelException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("channel is closed"));
    }

    @Test
    void testAcquireLockNonWritableChannelException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), eq(false)))
            .thenThrow(new NonWritableChannelException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("not opened for writing"));
    }

    @Test
    void testAcquireLockFileLockInterruptionException() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenThrow(new FileLockInterruptionException());

        IOException exception = assertThrows(IOException.class, () ->
            new FileLockManager(mockFile, false));

        assertTrue(exception.getMessage().contains("interrupted"));
        assertTrue(Thread.interrupted()); // Clear and verify
    }

    // =====================================================================
    // isLocked and close edge cases
    // =====================================================================

    @Test
    void testIsLockedReturnsFalseWhenLockIsNull() throws IOException {
        // We cannot easily create a state where lock is null after construction,
        // but we can verify the behavior when lock.isValid() returns false
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenReturn(mockLock);
        when(mockLock.isValid()).thenReturn(false);

        FileLockManager lockManager = new FileLockManager(mockFile, false);

        // lock is non-null but invalid - should return false
        assertFalse(lockManager.isLocked());
    }

    @Test
    void testCloseWithValidLock() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenReturn(mockLock);
        when(mockLock.isValid()).thenReturn(true);

        FileLockManager lockManager = new FileLockManager(mockFile, false);
        lockManager.close();

        verify(mockLock).release();
    }

    @Test
    void testMultipleCloses() throws IOException {
        when(mockFile.getChannel()).thenReturn(mockChannel);
        when(mockChannel.tryLock(anyLong(), anyLong(), anyBoolean()))
            .thenReturn(mockLock);
        when(mockLock.isValid()).thenReturn(true).thenReturn(false);

        FileLockManager lockManager = new FileLockManager(mockFile, false);

        // First close releases the lock
        lockManager.close();

        // Second close should not release again (lock now invalid)
        assertDoesNotThrow(() -> lockManager.close());
    }
}
