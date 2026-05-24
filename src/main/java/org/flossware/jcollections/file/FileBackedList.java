package org.flossware.jcollections.file;

import org.flossware.jcollections.file.cache.WriteCache;
import org.flossware.jcollections.file.format.EntryChecksum;
import org.flossware.jcollections.file.format.FileHeader;
import org.flossware.jcollections.file.locking.FileLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileBackedList<E extends Serializable> extends AbstractList<E> implements SequencedCollection<E>, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FileBackedList.class);

    // Maximum size for memory-mapped files (~2GB, safe for 32-bit and 64-bit JVMs)
    private static final long MAX_MAPPED_SIZE = Integer.MAX_VALUE;

    private final File filePath;
    private final RandomAccessFile file;
    private final FileChannel channel;
    private MappedByteBuffer mappedBuffer;
    private final ReentrantReadWriteLock lock;
    private final List<Long> offsets;
    private final AtomicInteger size;
    private final FileHeader header;
    private final FileLockManager lockManager;
    private final WriteCache<E> cache;
    private final boolean enableChecksums;
    private final boolean enableMmap;
    private final boolean enableCache;
    private long actualDataSize;

    public static class Builder<E extends Serializable> {
        private File path;
        private boolean enableChecksums = true;
        private boolean enableMmap = true;
        private boolean enableCache = true;
        private int cacheSize = 1000;
        private long cacheFlushMs = 5000;
        private boolean sharedLock = false;

        public Builder(File path) {
            if (path == null) {
                throw new IllegalArgumentException("File path cannot be null");
            }
            if (path.exists() && path.isDirectory()) {
                throw new IllegalArgumentException("Path must not be a directory: " + path);
            }
            this.path = path;
        }

        public Builder<E> enableChecksums(boolean enable) {
            this.enableChecksums = enable;
            return this;
        }

        public Builder<E> enableMmap(boolean enable) {
            this.enableMmap = enable;
            return this;
        }

        public Builder<E> enableCache(boolean enable) {
            this.enableCache = enable;
            return this;
        }

        public Builder<E> cacheSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Cache size must be positive, got: " + size);
            }
            this.cacheSize = size;
            return this;
        }

        public Builder<E> cacheFlushMs(long ms) {
            if (ms <= 0) {
                throw new IllegalArgumentException("Cache flush interval must be positive, got: " + ms);
            }
            this.cacheFlushMs = ms;
            return this;
        }

        public Builder<E> sharedLock(boolean shared) {
            this.sharedLock = shared;
            return this;
        }

        public FileBackedList<E> build() throws IOException {
            return new FileBackedList<>(this);
        }
    }

    private FileBackedList(Builder<E> builder) throws IOException {
        this.filePath = builder.path;
        this.file = new RandomAccessFile(builder.path, "rw");
        this.channel = file.getChannel();
        this.lock = new ReentrantReadWriteLock();
        this.offsets = new ArrayList<>();
        this.size = new AtomicInteger(0);
        this.enableChecksums = builder.enableChecksums;
        this.enableMmap = builder.enableMmap;
        this.enableCache = builder.enableCache;

        this.lockManager = new FileLockManager(file, builder.sharedLock);

        this.cache = builder.enableCache ?
            new WriteCache<>(builder.cacheSize, builder.cacheFlushMs) : null;

        FileHeader existingHeader = FileHeader.read(file);
        if (existingHeader == null) {
            int flags = 0;
            if (enableChecksums) flags |= FileHeader.FLAG_CHECKSUMS_ENABLED;
            if (enableMmap) flags |= FileHeader.FLAG_MMAP_ENABLED;

            this.header = new FileHeader(FileHeader.VERSION_2, flags);
            header.write(file);
            logger.info("Created new file-backed list: path={}, checksums={}, mmap={}, cache={}",
                builder.path, enableChecksums, enableMmap, enableCache);
        } else {
            this.header = existingHeader;
            logger.info("Opened existing file-backed list: path={}, version={}, size={}",
                builder.path, existingHeader.getVersion(), file.length());
        }

        this.actualDataSize = file.length();
        loadIndex();

        if (enableMmap) {
            remapBuffer();
        }
    }

    private void loadIndex() throws IOException {
        lock.writeLock().lock();
        try {
            if (actualDataSize <= FileHeader.getHeaderSize()) {
                return;
            }

            long position = FileHeader.getHeaderSize();
            while (position + 4 <= actualDataSize) {
                file.seek(position);
                int length = file.readInt();

                // Validate length field
                if (length < 0) {
                    throw new IOException("Corrupted entry at position " + position +
                        ": invalid negative length " + length);
                }

                long checksumSize = header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED) ? 8 : 0;
                long entrySize = 4 + checksumSize + length;

                // Check if entry would exceed file bounds
                if (position + entrySize > actualDataSize) {
                    throw new IOException("Corrupted entry at position " + position +
                        ": entry size " + entrySize + " bytes exceeds file bounds. " +
                        "File may be truncated or corrupted.");
                }

                // Verify checksum if enabled
                if (header.hasFlag(FileHeader.FLAG_CHECKSUMS_ENABLED)) {
                    long storedChecksum = file.readLong();

                    // Read and verify the data
                    byte[] data = new byte[length];
                    file.readFully(data);

                    if (!EntryChecksum.verifyChecksum(data, storedChecksum)) {
                        throw new IOException("Checksum mismatch at position " + position +
                            ". File may be corrupted. Expected checksum: " + storedChecksum +
                            ", actual: " + EntryChecksum.calculateChecksum(data));
                    }
                }

                offsets.add(position);
                position += entrySize;
                size.incrementAndGet();
            }
            logger.debug("Loaded {} entries from file, total size: {} bytes", size.get(), actualDataSize);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void remapBuffer() throws IOException {
        if (mappedBuffer != null) {
            mappedBuffer.force();
        }
        long fileSize = Math.max(file.length(), 1024 * 1024);

        if (fileSize > MAX_MAPPED_SIZE) {
            logger.error("File too large to memory-map: {} bytes (max: {} bytes)", fileSize, MAX_MAPPED_SIZE);
            throw new IOException("File too large to memory-map: " + fileSize +
                " bytes (max: " + MAX_MAPPED_SIZE + " bytes). " +
                "Consider disabling memory-mapped I/O for very large files.");
        }

        this.mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
        logger.debug("Remapped buffer: size={} bytes", fileSize);
    }

    @Override
    public E get(int index) {
        lock.readLock().lock();
        try {
            // Check index inside lock to prevent race condition
            Objects.checkIndex(index, size.get());

            if (enableCache && cache != null) {
                long offset = offsets.get(index);
                E cached = cache.get(offset);
                if (cached != null) {
                    return cached;
                }
            }

            long offset = offsets.get(index);

            // Use memory-mapped buffer if available for better performance
            if (enableMmap && mappedBuffer != null) {
                int pos = (int) offset;
                int length = mappedBuffer.getInt(pos);
                pos += 4;

                long storedChecksum = 0;
                if (enableChecksums) {
                    storedChecksum = mappedBuffer.getLong(pos);
                    pos += 8;
                }

                byte[] buffer = new byte[length];
                for (int i = 0; i < length; i++) {
                    buffer[i] = mappedBuffer.get(pos + i);
                }

                if (enableChecksums && !EntryChecksum.verifyChecksum(buffer, storedChecksum)) {
                    throw new IOException("Checksum mismatch at index " + index);
                }

                return deserialize(buffer);
            } else {
                // Fallback to RandomAccessFile
                file.seek(offset);
                int length = file.readInt();
                long storedChecksum = enableChecksums ? file.readLong() : 0;

                byte[] buffer = new byte[length];
                file.readFully(buffer);

                if (enableChecksums && !EntryChecksum.verifyChecksum(buffer, storedChecksum)) {
                    throw new IOException("Checksum mismatch at index " + index);
                }

                return deserialize(buffer);
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new UncheckedIOException(new IOException(e));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public boolean add(E e) {
        lock.writeLock().lock();
        try {
            flushCacheIfNeeded();

            byte[] data = serialize(e);
            long offset = actualDataSize;

            file.seek(offset);
            file.writeInt(data.length);

            if (enableChecksums) {
                long checksum = EntryChecksum.calculateChecksum(data);
                file.writeLong(checksum);
            }

            file.write(data);
            offsets.add(offset);
            size.incrementAndGet();

            actualDataSize = file.getFilePointer();

            if (enableCache && cache != null) {
                cache.put(offset, e, data);
            }

            if (enableMmap && mappedBuffer != null && actualDataSize > mappedBuffer.capacity()) {
                remapBuffer();
            }

            return true;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void flushCacheIfNeeded() throws IOException {
        if (!enableCache || cache == null) {
            return;
        }

        // Use atomic check-and-get to avoid race condition
        List<WriteCache.CachedEntry<E>> pendingWrites = cache.getPendingWritesIfNeeded();
        for (WriteCache.CachedEntry<E> entry : pendingWrites) {
            file.seek(entry.offset);
            file.writeInt(entry.serialized.length);

            if (enableChecksums) {
                long checksum = EntryChecksum.calculateChecksum(entry.serialized);
                file.writeLong(checksum);
            }

            file.write(entry.serialized);
        }
    }

    public void flush() throws IOException {
        lock.writeLock().lock();
        try {
            if (enableCache && cache != null) {
                flushCacheIfNeeded();
            }
            if (mappedBuffer != null) {
                mappedBuffer.force();
            }
            file.setLength(actualDataSize);
            channel.force(true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Flushes pending writes to disk. Lists do not support true compaction
     * since they don't have duplicate entries to remove like maps do.
     * For space reclamation in maps, see {@link FileBackedMap#compact()}.
     *
     * @throws IOException if flush fails
     * @see #flush()
     */
    public void compact() throws IOException {
        lock.writeLock().lock();
        try {
            flush();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void addFirst(E e) {
        throw new UnsupportedOperationException("addFirst not supported (requires rewriting file)");
    }

    @Override
    public void addLast(E e) {
        add(e);
    }

    @Override
    public E getFirst() {
        if (size.get() == 0) {
            throw new java.util.NoSuchElementException();
        }
        return get(0);
    }

    @Override
    public E getLast() {
        if (size.get() == 0) {
            throw new java.util.NoSuchElementException();
        }
        return get(size.get() - 1);
    }

    @Override
    public E removeFirst() {
        throw new UnsupportedOperationException("removeFirst not supported (requires rewriting file)");
    }

    @Override
    public E removeLast() {
        lock.writeLock().lock();
        try {
            if (size.get() == 0) {
                throw new java.util.NoSuchElementException();
            }
            flush();
            int lastIndex = size.get() - 1;
            E element = get(lastIndex);
            long offset = offsets.remove(lastIndex);
            file.setLength(offset);
            actualDataSize = offset;
            size.decrementAndGet();

            if (enableMmap) {
                remapBuffer();
            }

            return element;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            if (enableCache && cache != null) {
                flushCacheIfNeeded();
                cache.close();
            }
            if (mappedBuffer != null) {
                mappedBuffer.force();
                unmapBuffer();
            }
            if (file != null && channel.isOpen()) {
                file.setLength(actualDataSize);
                channel.force(true);
            }
            if (lockManager != null) {
                lockManager.close();
            }
            if (file != null) {
                file.close();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private byte[] serialize(E obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private E deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (E) ois.readObject();
        }
    }

    public FileHeader getHeader() {
        return header;
    }

    public File getFilePath() {
        return filePath;
    }
}
