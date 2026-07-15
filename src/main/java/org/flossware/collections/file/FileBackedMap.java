package org.flossware.collections.file;

import org.flossware.collections.file.index.BTreeIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;

public class FileBackedMap<K extends Serializable & Comparable<K>, V extends Serializable> extends AbstractMap<K, V> implements SequencedMap<K, V>, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FileBackedMap.class);

    private FileBackedList<AbstractMap.SimpleEntry<K, V>> entryList;
    private final BTreeIndex<K> index;
    private final boolean useBTreeIndex;
    private final boolean isView;

    public static class Builder<K extends Serializable & Comparable<K>, V extends Serializable> {
        private File path;
        private boolean enableChecksums = true;
        private boolean enableMmap = true;
        private boolean enableCache = true;
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

        public Builder<K, V> enableChecksums(boolean enable) {
            this.enableChecksums = enable;
            return this;
        }

        public Builder<K, V> enableMmap(boolean enable) {
            this.enableMmap = enable;
            return this;
        }

        public Builder<K, V> enableCache(boolean enable) {
            this.enableCache = enable;
            return this;
        }

        public Builder<K, V> enableBTreeIndex(boolean enable) {
            this.enableBTreeIndex = enable;
            return this;
        }

        public Builder<K, V> cacheSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Cache size must be positive, got: " + size);
            }
            this.cacheSize = size;
            return this;
        }

        public Builder<K, V> cacheFlushMs(long ms) {
            if (ms <= 0) {
                throw new IllegalArgumentException("Cache flush interval must be positive, got: " + ms);
            }
            this.cacheFlushMs = ms;
            return this;
        }

        public FileBackedMap<K, V> build() throws IOException {
            return new FileBackedMap<>(this);
        }
    }

    private FileBackedMap(Builder<K, V> builder) throws IOException {
        FileBackedList.Builder<AbstractMap.SimpleEntry<K, V>> listBuilder =
            new FileBackedList.Builder<AbstractMap.SimpleEntry<K, V>>(builder.path)
                .enableChecksums(builder.enableChecksums)
                .enableMmap(builder.enableMmap)
                .enableCache(builder.enableCache)
                .cacheSize(builder.cacheSize)
                .cacheFlushMs(builder.cacheFlushMs);

        this.entryList = listBuilder.build();
        this.useBTreeIndex = builder.enableBTreeIndex;
        this.index = useBTreeIndex ? new BTreeIndex<>() : null;
        this.isView = false;

        if (useBTreeIndex) {
            rebuildIndex();
        }
    }

    private FileBackedMap(List<AbstractMap.SimpleEntry<K, V>> entryList, BTreeIndex<K> index, boolean useBTree) {
        this.entryList = (FileBackedList<AbstractMap.SimpleEntry<K, V>>) entryList;
        this.index = index;
        this.useBTreeIndex = useBTree;
        this.isView = true;
    }

    private void rebuildIndex() {
        if (!useBTreeIndex || index == null) {
            return;
        }

        logger.debug("Rebuilding BTree index from {} entries", entryList.size());
        index.clear();
        for (int i = 0; i < entryList.size(); i++) {
            AbstractMap.SimpleEntry<K, V> entry = entryList.get(i);
            index.put(entry.getKey(), i);
        }
        logger.debug("BTree index rebuilt: {} unique keys indexed", index.getAll().size());
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Map<K, V> uniqueEntries = new LinkedHashMap<>();
        for (AbstractMap.SimpleEntry<K, V> entry : entryList) {
            uniqueEntries.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableSet(uniqueEntries.entrySet());
    }

    @Override
    public V get(Object key) {
        if (useBTreeIndex && index != null && key instanceof Comparable) {
            @SuppressWarnings("unchecked")
            K k = (K) key;
            Long offset = index.get(k);
            if (offset != null) {
                AbstractMap.SimpleEntry<K, V> entry = entryList.get(offset.intValue());
                if (Objects.equals(entry.getKey(), key)) {
                    return entry.getValue();
                }
            }
        }

        V lastMatch = null;
        for (Map.Entry<K, V> entry : entryList) {
            if (Objects.equals(entry.getKey(), key)) {
                lastMatch = entry.getValue();
            }
        }
        return lastMatch;
    }

    @Override
    public boolean containsKey(Object key) {
        if (useBTreeIndex && index != null && key instanceof Comparable) {
            @SuppressWarnings("unchecked")
            K k = (K) key;
            Long offset = index.get(k);
            if (offset != null) {
                // Verify the key actually exists at that offset
                try {
                    AbstractMap.SimpleEntry<K, V> entry = entryList.get(offset.intValue());
                    return Objects.equals(entry.getKey(), key);
                } catch (IndexOutOfBoundsException e) {
                    // Index is out of sync - fall through to linear search
                }
            }
        }

        // Linear search fallback
        for (Map.Entry<K, V> entry : entryList) {
            if (Objects.equals(entry.getKey(), key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V put(K key, V value) {
        if (isView) {
            throw new UnsupportedOperationException("Modifications not supported on reversed views");
        }

        V oldValue = get(key);
        int newIndex = entryList.size();
        entryList.add(new AbstractMap.SimpleEntry<>(key, value));

        if (useBTreeIndex && index != null) {
            index.put(key, newIndex);
        }

        return oldValue;
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("remove not supported (use compact() to reclaim space)");
    }

    public void compact() throws IOException {
        if (isView) {
            throw new UnsupportedOperationException("compact not supported on views");
        }

        long originalSize = entryList.size();
        long originalFileSize = entryList.getFilePath().length();
        logger.info("Starting compaction: {} entries, {} bytes", originalSize, originalFileSize);

        Map<K, V> uniqueEntries = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entryList) {
            uniqueEntries.put(entry.getKey(), entry.getValue());
        }

        File originalFile = entryList.getFilePath();
        File tempFile = File.createTempFile("jcollections-compact", ".tmp", originalFile.getParentFile());

        // Save settings before closing to avoid accessing closed object
        boolean hasChecksums = entryList.getHeader().hasFlag(
            org.flossware.collections.file.format.FileHeader.FLAG_CHECKSUMS_ENABLED);
        boolean hasMmap = entryList.getHeader().hasFlag(
            org.flossware.collections.file.format.FileHeader.FLAG_MMAP_ENABLED);

        try (FileBackedMap<K, V> tempMap = new Builder<K, V>(tempFile)
                .enableChecksums(hasChecksums)
                .enableMmap(hasMmap)
                .enableBTreeIndex(useBTreeIndex)
                .build()) {

            for (Map.Entry<K, V> entry : uniqueEntries.entrySet()) {
                tempMap.put(entry.getKey(), entry.getValue());
            }
            tempMap.flush();
        }

        // Close the current file before replacing
        entryList.close();

        // Replace original file with compacted temp file using NIO for cross-platform reliability
        Path originalPath = originalFile.toPath();
        Path tempPath = tempFile.toPath();

        try {
            // Try atomic move first (works on most platforms if same filesystem)
            Files.move(tempPath, originalPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Fallback to non-atomic move (works across filesystems)
            logger.debug("Atomic move not supported, using regular move");
            Files.move(tempPath, originalPath,
                StandardCopyOption.REPLACE_EXISTING);
        }

        // Reopen the compacted file with saved settings
        FileBackedList.Builder<AbstractMap.SimpleEntry<K, V>> listBuilder =
            new FileBackedList.Builder<AbstractMap.SimpleEntry<K, V>>(originalFile)
                .enableChecksums(hasChecksums)
                .enableMmap(hasMmap)
                .enableCache(true);

        this.entryList = listBuilder.build();

        // Rebuild the index with the compacted data
        if (useBTreeIndex) {
            rebuildIndex();
        }

        long newSize = entryList.size();
        long newFileSize = originalFile.length();
        long savedBytes = originalFileSize - newFileSize;
        long removedDuplicates = originalSize - newSize;
        logger.info("Compaction complete: {} unique entries (removed {} duplicates), saved {} bytes ({} -> {})",
            newSize, removedDuplicates, savedBytes, originalFileSize, newFileSize);
    }

    public void flush() throws IOException {
        entryList.flush();
    }

    @Override
    public SequencedMap<K, V> reversed() {
        return new ReversedMapView<>(this);
    }

    /**
     * Reversed view of a FileBackedMap.
     * Provides reversed iteration while delegating all other operations to the original map.
     * Does not share the BTreeIndex - uses linear search for correctness.
     */
    private static class ReversedMapView<K extends Serializable & Comparable<K>, V extends Serializable>
            extends AbstractMap<K, V> implements SequencedMap<K, V> {

        private final FileBackedMap<K, V> delegate;

        ReversedMapView(FileBackedMap<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new AbstractSet<Entry<K, V>>() {
                @Override
                public Iterator<Entry<K, V>> iterator() {
                    // Return reversed iterator from delegate's entryList
                    Iterator<AbstractMap.SimpleEntry<K, V>> reversedIterator =
                        delegate.entryList.reversed().iterator();

                    // Wrap iterator to convert SimpleEntry to Entry
                    return new Iterator<Entry<K, V>>() {
                        @Override
                        public boolean hasNext() {
                            return reversedIterator.hasNext();
                        }

                        @Override
                        public Entry<K, V> next() {
                            return reversedIterator.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return delegate.size();
                }
            };
        }

        @Override
        public V get(Object key) {
            // Delegate get() to original map - it works correctly for all keys
            return delegate.get(key);
        }

        @Override
        public V put(K key, V value) {
            throw new UnsupportedOperationException("Modifications not supported on reversed views");
        }

        @Override
        public SequencedMap<K, V> reversed() {
            // Reversing a reversed view returns the original
            return delegate;
        }

        @Override
        public Entry<K, V> firstEntry() {
            return delegate.lastEntry();
        }

        @Override
        public Entry<K, V> lastEntry() {
            return delegate.firstEntry();
        }

        @Override
        public Entry<K, V> pollFirstEntry() {
            throw new UnsupportedOperationException("pollFirstEntry not supported on reversed view");
        }

        @Override
        public Entry<K, V> pollLastEntry() {
            throw new UnsupportedOperationException("pollLastEntry not supported on reversed view");
        }

        @Override
        public V putFirst(K k, V v) {
            throw new UnsupportedOperationException("putFirst not supported on reversed view");
        }

        @Override
        public V putLast(K k, V v) {
            throw new UnsupportedOperationException("putLast not supported on reversed view");
        }
    }

    @Override
    public Entry<K, V> firstEntry() {
        return entryList.isEmpty() ? null : entryList.getFirst();
    }

    @Override
    public Entry<K, V> lastEntry() {
        return entryList.isEmpty() ? null : entryList.getLast();
    }

    @Override
    public Entry<K, V> pollFirstEntry() {
        throw new UnsupportedOperationException("pollFirstEntry not supported (requires rewriting file)");
    }

    @Override
    public Entry<K, V> pollLastEntry() {
        if (isView) {
            throw new UnsupportedOperationException("pollLastEntry not supported on reversed view");
        }
        Entry<K, V> last = entryList.isEmpty() ? null : entryList.removeLast();
        if (useBTreeIndex && last != null) {
            rebuildIndex();
        }
        return last;
    }

    @Override
    public V putFirst(K k, V v) {
        throw new UnsupportedOperationException("putFirst not supported (requires rewriting file)");
    }

    @Override
    public V putLast(K k, V v) {
        if (isView) {
            throw new UnsupportedOperationException("putLast not supported on reversed view");
        }
        V oldValue = get(k);
        entryList.addLast(new AbstractMap.SimpleEntry<>(k, v));

        if (useBTreeIndex && index != null) {
            index.put(k, entryList.size() - 1);
        }

        return oldValue;
    }

    @Override
    public void close() throws IOException {
        if (!isView) {
            entryList.close();
        }
    }

    public int getIndexedKeyCount() {
        if (useBTreeIndex && index != null) {
            return index.getAll().size();
        }
        return 0;
    }
}
