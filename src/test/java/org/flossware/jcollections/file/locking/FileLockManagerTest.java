package org.flossware.jcollections.file.locking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLockManagerTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private RandomAccessFile raf;
    private FileLockManager lockManager;

    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test-lock.bin").toFile();
        raf = new RandomAccessFile(testFile, "rw");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (lockManager != null) {
            lockManager.close();
        }
        if (raf != null) {
            raf.close();
        }
    }

    @Test
    void testCreateExclusiveLock() throws IOException {
        lockManager = new FileLockManager(raf, false);
        assertTrue(lockManager.isLocked());
    }

    @Test
    void testCreateSharedLock() throws IOException {
        lockManager = new FileLockManager(raf, true);
        assertTrue(lockManager.isLocked());
    }

    @Test
    void testUpgradeToExclusive() throws IOException {
        lockManager = new FileLockManager(raf, true);
        assertTrue(lockManager.isLocked());
        assertDoesNotThrow(() -> lockManager.upgradeToExclusive());
        assertTrue(lockManager.isLocked());
    }

    @Test
    void testClose() throws IOException {
        lockManager = new FileLockManager(raf, false);
        assertTrue(lockManager.isLocked());
        lockManager.close();
        assertFalse(lockManager.isLocked());
    }

    @Test
    void testLockWithFileOperations() throws IOException {
        lockManager = new FileLockManager(raf, false);
        assertTrue(lockManager.isLocked());
        raf.writeUTF("test data");
        raf.seek(0);
        String data = raf.readUTF();
        assertNotNull(data);
        assertEquals("test data", data);
    }

    @Test
    void testMultipleClose() throws IOException {
        lockManager = new FileLockManager(raf, false);
        lockManager.close();
        assertDoesNotThrow(() -> lockManager.close()); // Should not throw
    }

    @Test
    void testClosedChannelException() throws IOException {
        raf.close(); // Close before creating lock manager
        assertThrows(IOException.class, () -> new FileLockManager(raf, false));
    }

    @Test
    void testUpgradeWhenAlreadyExclusive() throws IOException {
        lockManager = new FileLockManager(raf, false); // Already exclusive
        assertTrue(lockManager.isLocked());
        // Upgrading when already exclusive should be no-op
        assertDoesNotThrow(() -> lockManager.upgradeToExclusive());
        assertTrue(lockManager.isLocked());
    }

    @Test
    void testReadOnlyFileExclusiveLock() throws IOException {
        // Create a read-only file
        if (raf != null) {
            raf.close();
        }
        raf = new RandomAccessFile(testFile, "r");
        // Trying to get exclusive lock on read-only should fail
        assertThrows(IOException.class, () -> new FileLockManager(raf, false));
    }

    @Test
    void testSharedLockOnReadOnlyFile() throws IOException {
        // Create a read-only file
        if (raf != null) {
            raf.close();
        }
        raf = new RandomAccessFile(testFile, "r");
        // Shared lock on read-only should work
        lockManager = new FileLockManager(raf, true);
        assertTrue(lockManager.isLocked());
    }
}
