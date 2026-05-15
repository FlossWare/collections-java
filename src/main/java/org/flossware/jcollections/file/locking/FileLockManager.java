package org.flossware.jcollections.file.locking;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
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
        }
    }

    public void upgradeToExclusive() throws IOException {
        if (!shared) {
            return;
        }
        if (lock != null) {
            lock.release();
        }
        this.lock = channel.tryLock(0, Long.MAX_VALUE, false);
        if (lock == null) {
            throw new IOException("Could not upgrade to exclusive lock");
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
