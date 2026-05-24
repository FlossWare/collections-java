package org.flossware.jcollections.file.locking;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;

public class FileLockManager implements AutoCloseable {
    private final FileChannel channel;
    private FileLock lock;
    private final boolean shared;

    public FileLockManager(RandomAccessFile file, boolean shared) throws IOException {
        this.channel = file.getChannel();
        this.shared = shared;
        acquireLock();
    }

    private void acquireLock() throws IOException {
        try {
            this.lock = channel.tryLock(0, Long.MAX_VALUE, shared);
            if (lock == null) {
                throw new IOException("Could not acquire file lock - file may be in use by another process");
            }
        } catch (OverlappingFileLockException e) {
            throw new IOException("File is already locked by this JVM", e);
        } catch (ClosedChannelException e) {
            throw new IOException("Cannot lock - channel is closed", e);
        } catch (FileLockInterruptionException e) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
            throw new IOException("File lock interrupted", e);
        } catch (NonWritableChannelException e) {
            throw new IOException("Channel not opened for writing - cannot acquire exclusive lock", e);
        } catch (NonReadableChannelException e) {
            throw new IOException("Channel not opened for reading - cannot acquire shared lock", e);
        }
    }

    public void upgradeToExclusive() throws IOException {
        if (!shared) {
            return;
        }
        if (lock != null) {
            lock.release();
        }
        try {
            this.lock = channel.tryLock(0, Long.MAX_VALUE, false);
            if (lock == null) {
                throw new IOException("Could not upgrade to exclusive lock");
            }
        } catch (OverlappingFileLockException e) {
            throw new IOException("Cannot upgrade - file is already locked", e);
        } catch (ClosedChannelException e) {
            throw new IOException("Cannot upgrade lock - channel is closed", e);
        } catch (FileLockInterruptionException e) {
            Thread.currentThread().interrupt(); // Preserve interrupt status
            throw new IOException("Lock upgrade interrupted", e);
        } catch (NonWritableChannelException e) {
            throw new IOException("Channel not opened for writing - cannot upgrade to exclusive lock", e);
        }
    }

    public boolean isLocked() {
        return lock != null && lock.isValid();
    }

    @Override
    public void close() throws IOException {
        if (lock != null && lock.isValid()) {
            lock.release();
        }
    }
}
