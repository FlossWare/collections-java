package org.flossware.collections.file.primitive;

import org.flossware.collections.file.format.FileHeader;
import org.flossware.collections.file.locking.FileLockManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DoubleList implements AutoCloseable {
    private static final int DOUBLE_SIZE = 8;
    // Maximum size for memory-mapped files (~2GB, safe for 32-bit and 64-bit JVMs)
    private static final long MAX_MAPPED_SIZE = Integer.MAX_VALUE;

    private final RandomAccessFile file;
    private final FileChannel channel;
    private MappedByteBuffer mappedBuffer;
    private final ReentrantReadWriteLock lock;
    private final AtomicInteger size;
    private final FileHeader header;
    private final FileLockManager lockManager;

    public DoubleList(File path) throws IOException {
        this.file = new RandomAccessFile(path, "rw");
        this.channel = file.getChannel();
        this.lock = new ReentrantReadWriteLock();
        this.size = new AtomicInteger(0);
        this.lockManager = new FileLockManager(file, false);

        FileHeader existingHeader = FileHeader.read(file);
        if (existingHeader == null) {
            this.header = new FileHeader(FileHeader.VERSION_2, FileHeader.FLAG_MMAP_ENABLED);
            header.write(file);
            this.size.set(0);
            file.setLength(FileHeader.getHeaderSize());
        } else {
            this.header = existingHeader;
            long dataSize = file.length() - FileHeader.getHeaderSize();
            if (dataSize < 0) dataSize = 0;
            long count = dataSize / DOUBLE_SIZE;
            this.size.set((int) count);
        }

        remapBuffer();
    }

    private void remapBuffer() throws IOException {
        if (mappedBuffer != null) {
            mappedBuffer.force();
        }
        long minSize = FileHeader.getHeaderSize() + (long) size.get() * DOUBLE_SIZE;
        long fileSize = Math.max(file.length(), minSize + 1024 * DOUBLE_SIZE);

        if (file.length() < minSize) {
            file.setLength(minSize);
        }

        if (fileSize > MAX_MAPPED_SIZE) {
            throw new IOException("File too large to memory-map: " + fileSize +
                " bytes (max: " + MAX_MAPPED_SIZE + " bytes). " +
                "Maximum DoubleList size is " + (MAX_MAPPED_SIZE / DOUBLE_SIZE) + " doubles.");
        }

        this.mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
    }

    public double get(int index) {
        if (index < 0 || index >= size.get()) {
            throw new IndexOutOfBoundsException(index);
        }

        lock.readLock().lock();
        try {
            long position = FileHeader.getHeaderSize() + (long) index * DOUBLE_SIZE;
            if (position > Integer.MAX_VALUE) {
                throw new IllegalStateException("Position overflow: list too large");
            }
            return mappedBuffer.getDouble((int) position);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void add(double value) {
        lock.writeLock().lock();
        try {
            int index = size.get();
            long position = FileHeader.getHeaderSize() + (long) index * DOUBLE_SIZE;
            if (position > Integer.MAX_VALUE) {
                throw new IllegalStateException("Position overflow: list too large");
            }

            long requiredSize = position + DOUBLE_SIZE;
            if (requiredSize > file.length()) {
                long newSize = Math.max(requiredSize, file.length() + 1024 * DOUBLE_SIZE);
                file.setLength(newSize);
                remapBuffer();
            }

            mappedBuffer.putDouble((int) position, value);
            size.incrementAndGet();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        return size.get();
    }

    public void flush() {
        lock.writeLock().lock();
        try {
            if (mappedBuffer != null) {
                mappedBuffer.force();
            }
            long actualSize = FileHeader.getHeaderSize() + (long) size.get() * DOUBLE_SIZE;
            try {
                if (channel.isOpen()) {
                    file.setLength(actualSize);
                    channel.force(true);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Explicitly unmaps the MappedByteBuffer to release file lock.
     * Uses reflection to call internal cleaner methods.
     * Important for Windows where mapped files cannot be deleted until unmapped.
     */
    private void unmapBuffer() {
        if (mappedBuffer == null) {
            return;
        }

        try {
            // Try Java 8 approach first
            Method cleanerMethod = mappedBuffer.getClass().getMethod("cleaner");
            cleanerMethod.setAccessible(true);
            Object cleaner = cleanerMethod.invoke(mappedBuffer);
            if (cleaner != null) {
                Method cleanMethod = cleaner.getClass().getMethod("clean");
                cleanMethod.invoke(cleaner);
            }
        } catch (Exception e) {
            // Try Java 9+ approach
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Object unsafe = unsafeField.get(null);
                Method invokeCleaner = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                invokeCleaner.invoke(unsafe, mappedBuffer);
            } catch (Exception ex) {
                // Fall back to letting GC handle it
            }
        }

        mappedBuffer = null;
    }

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (mappedBuffer != null) {
                mappedBuffer.force();
                unmapBuffer();
            }
            long actualSize = FileHeader.getHeaderSize() + (long) size.get() * DOUBLE_SIZE;
            if (file != null && channel.isOpen()) {
                file.setLength(actualSize);
                channel.force(true);
            }
            if (lockManager != null) {
                lockManager.close();
            }
            if (channel != null) {
                channel.close();
            }
            if (file != null) {
                file.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
