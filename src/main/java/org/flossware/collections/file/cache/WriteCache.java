package org.flossware.collections.file.cache;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class WriteCache<T extends Serializable> implements AutoCloseable {
    private final Map<Long, CachedEntry<T>> cache;
    private final List<CachedEntry<T>> pendingWrites;
    private final int maxCacheSize;
    private final long maxCacheTimeMs;
    private final int maxPendingWrites;
    private final ReentrantReadWriteLock lock;
    private long lastFlushTime;

    public static class CachedEntry<T> {
        public final long offset;
        public final T value;
        public final byte[] serialized;
        public final long timestamp;

        public CachedEntry(long offset, T value, byte[] serialized) {
            this.offset = offset;
            this.value = value;
            this.serialized = serialized;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public WriteCache(int maxCacheSize, long maxCacheTimeMs) {
        this.maxCacheSize = maxCacheSize;
        this.maxCacheTimeMs = maxCacheTimeMs;
        // Limit pending writes to 10x cache size to prevent unbounded memory growth
        this.maxPendingWrites = maxCacheSize * 10;
        this.cache = new LinkedHashMap<>(maxCacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, CachedEntry<T>> eldest) {
                return size() > maxCacheSize;
            }
        };
        this.pendingWrites = new ArrayList<>();
        this.lock = new ReentrantReadWriteLock();
        this.lastFlushTime = System.currentTimeMillis();
    }

    public T get(long offset) {
        lock.readLock().lock();
        try {
            CachedEntry<T> entry = cache.get(offset);
            return entry != null ? entry.value : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(long offset, T value, byte[] serialized) {
        lock.writeLock().lock();
        try {
            // Prevent unbounded memory growth by enforcing hard limit on pending writes
            if (pendingWrites.size() >= maxPendingWrites) {
                throw new IllegalStateException(
                    "Too many pending writes (" + pendingWrites.size() + " >= " + maxPendingWrites + "). " +
                    "Call flush() to persist data before continuing.");
            }

            CachedEntry<T> entry = new CachedEntry<>(offset, value, serialized);
            cache.put(offset, entry);
            pendingWrites.add(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean shouldFlush() {
        lock.readLock().lock();
        try {
            if (pendingWrites.size() >= maxCacheSize) {
                return true;
            }
            long elapsed = System.currentTimeMillis() - lastFlushTime;
            return elapsed >= maxCacheTimeMs && !pendingWrites.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<CachedEntry<T>> getPendingWrites() {
        lock.writeLock().lock();
        try {
            // Check again under write lock to avoid race condition
            if (pendingWrites.isEmpty()) {
                return new ArrayList<>();
            }
            List<CachedEntry<T>> writes = new ArrayList<>(pendingWrites);
            pendingWrites.clear();
            lastFlushTime = System.currentTimeMillis();
            return writes;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Atomically checks if flush is needed and returns pending writes.
     * Thread-safe alternative to calling shouldFlush() then getPendingWrites().
     * @return List of pending writes if flush is needed, empty list otherwise
     */
    public List<CachedEntry<T>> getPendingWritesIfNeeded() {
        lock.writeLock().lock();
        try {
            // Atomic check-and-get under write lock
            if (pendingWrites.size() >= maxCacheSize) {
                List<CachedEntry<T>> writes = new ArrayList<>(pendingWrites);
                pendingWrites.clear();
                lastFlushTime = System.currentTimeMillis();
                return writes;
            }
            long elapsed = System.currentTimeMillis() - lastFlushTime;
            if (elapsed >= maxCacheTimeMs && !pendingWrites.isEmpty()) {
                List<CachedEntry<T>> writes = new ArrayList<>(pendingWrites);
                pendingWrites.clear();
                lastFlushTime = System.currentTimeMillis();
                return writes;
            }
            return new ArrayList<>();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            pendingWrites.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void close() {
        clear();
    }
}
