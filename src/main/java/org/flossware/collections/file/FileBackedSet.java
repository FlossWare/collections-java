package org.flossware.collections.file;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.SequencedMap;
import java.util.SequencedSet;

public class FileBackedSet<E extends Serializable & Comparable<E>> extends AbstractSet<E> implements SequencedSet<E>, AutoCloseable {
    private final SequencedMap<E, Boolean> backingMap;
    private final boolean ownsMap;
    private static final Boolean PRESENT = Boolean.TRUE;

    public static class Builder<E extends Serializable & Comparable<E>> {
        private File path;
        private boolean enableChecksums = true;
        private boolean enableMmap = true;
        private boolean enableCache = true;
        private boolean enableFsync = false;
        private boolean enableBTreeIndex = true;
        private int cacheSize = 1000;
        private long cacheFlushMs = 5000;

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

        public Builder<E> enableFsync(boolean enable) {
            this.enableFsync = enable;
            return this;
        }

        public Builder<E> enableBTreeIndex(boolean enable) {
            this.enableBTreeIndex = enable;
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

        public FileBackedSet<E> build() throws IOException {
            return new FileBackedSet<>(this);
        }
    }

    private FileBackedSet(Builder<E> builder) throws IOException {
        FileBackedMap.Builder<E, Boolean> mapBuilder =
            new FileBackedMap.Builder<E, Boolean>(builder.path)
                .enableChecksums(builder.enableChecksums)
                .enableMmap(builder.enableMmap)
                .enableCache(builder.enableCache)
                .enableFsync(builder.enableFsync)
                .enableBTreeIndex(builder.enableBTreeIndex)
                .cacheSize(builder.cacheSize)
                .cacheFlushMs(builder.cacheFlushMs);

        this.backingMap = mapBuilder.build();
        this.ownsMap = true;
    }

    private FileBackedSet(SequencedMap<E, Boolean> map) {
        this.backingMap = map;
        this.ownsMap = false;
    }

    @Override
    public Iterator<E> iterator() {
        return backingMap.keySet().iterator();
    }

    @Override
    public int size() {
        return backingMap.size();
    }

    @Override
    public boolean add(E e) {
        if (backingMap.containsKey(e)) {
            return false;
        }
        backingMap.put(e, PRESENT);
        return true;
    }

    @Override
    public void addFirst(E e) {
        throw new UnsupportedOperationException("addFirst not supported (requires rewriting file)");
    }

    @Override
    public void addLast(E e) {
        backingMap.putLast(e, PRESENT);
    }

    @Override
    public E getFirst() {
        var entry = backingMap.firstEntry();
        if (entry == null) {
            throw new java.util.NoSuchElementException();
        }
        return entry.getKey();
    }

    @Override
    public E getLast() {
        var entry = backingMap.lastEntry();
        if (entry == null) {
            throw new java.util.NoSuchElementException();
        }
        return entry.getKey();
    }

    @Override
    public E removeFirst() {
        throw new UnsupportedOperationException("removeFirst not supported (requires rewriting file)");
    }

    @Override
    public E removeLast() {
        var entry = backingMap.pollLastEntry();
        return (entry != null) ? entry.getKey() : null;
    }

    @Override
    public SequencedSet<E> reversed() {
        return new FileBackedSet<E>(backingMap.reversed());
    }

    public void flush() throws IOException {
        if (ownsMap && backingMap instanceof FileBackedMap) {
            ((FileBackedMap<E, Boolean>) backingMap).flush();
        }
    }

    public void compact() throws IOException {
        if (ownsMap && backingMap instanceof FileBackedMap) {
            ((FileBackedMap<E, Boolean>) backingMap).compact();
        }
    }

    @Override
    public void close() throws IOException {
        if (ownsMap && backingMap instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("Error closing backing map", e);
            }
        }
    }
}
