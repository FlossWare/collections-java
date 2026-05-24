package org.flossware.jcollections.file.primitive;

import org.flossware.jcollections.file.format.FileHeader;
import org.flossware.jcollections.file.locking.FileLockManager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IntList implements AutoCloseable {
    private static final int INT_SIZE = 4;
    private final RandomAccessFile file;
    private final FileChannel channel;
    private MappedByteBuffer mappedBuffer;
    private final ReentrantReadWriteLock lock;
    private final AtomicInteger size;
    private final FileHeader header;
    private final FileLockManager lockManager;

    public IntList(File path) throws IOException {
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
            long count = dataSize / INT_SIZE;
            this.size.set((int) count);
        }

        remapBuffer();
    }

    private void remapBuffer() throws IOException {
        if (mappedBuffer != null) {
            mappedBuffer.force();
        }
        long minSize = FileHeader.getHeaderSize() + size.get() * INT_SIZE;
        long fileSize = Math.max(file.length(), minSize + 1024 * INT_SIZE);

        if (file.length() < minSize) {
            file.setLength(minSize);
        }

        this.mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
    }

    public int get(int index) {
        if (index < 0 || index >= size.get()) {
            throw new IndexOutOfBoundsException(index);
        }

        lock.readLock().lock();
        try {
            int position = FileHeader.getHeaderSize() + index * INT_SIZE;
            return mappedBuffer.getInt(position);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void add(int value) {
        lock.writeLock().lock();
        try {
            int index = size.get();
            int position = FileHeader.getHeaderSize() + index * INT_SIZE;

            long requiredSize = position + INT_SIZE;
            if (requiredSize > file.length()) {
                long newSize = Math.max(requiredSize, file.length() + 1024 * INT_SIZE);
                file.setLength(newSize);
                remapBuffer();
            }

            mappedBuffer.putInt(position, value);
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
            long actualSize = FileHeader.getHeaderSize() + size.get() * INT_SIZE;
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

    @Override
    public void close() throws IOException {
        lock.writeLock().lock();
        try {
            if (mappedBuffer != null) {
                mappedBuffer.force();
            }
            long actualSize = FileHeader.getHeaderSize() + size.get() * INT_SIZE;
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
