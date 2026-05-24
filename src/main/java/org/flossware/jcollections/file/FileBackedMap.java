package org.flossware.jcollections.file;

import org.flossware.jcollections.file.index.BTreeIndex;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.Set;

public class FileBackedMap<K extends Serializable & Comparable<K>, V extends Serializable> extends AbstractMap<K, V> implements SequencedMap<K, V>, AutoCloseable {
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
            this.cacheSize = size;
            return this;
        }

        public Builder<K, V> cacheFlushMs(long ms) {
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

        index.clear();
        for (int i = 0; i < entryList.size(); i++) {
            AbstractMap.SimpleEntry<K, V> entry = entryList.get(i);
            index.put(entry.getKey(), i);
        }
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                Iterator<AbstractMap.SimpleEntry<K, V>> it = entryList.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public Map.Entry<K, V> next() {
                        return (Map.Entry<K, V>) it.next();
                    }
                };
            }

            @Override
            public int size() {
                return entryList.size();
            }
        };
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

        for (Map.Entry<K, V> entry : entryList) {
            if (Objects.equals(entry.getKey(), key)) {
                return entry.getValue();
            }
        }
        return null;
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

        Map<K, V> uniqueEntries = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : entryList) {
            uniqueEntries.put(entry.getKey(), entry.getValue());
        }

        File originalFile = entryList.getFilePath();
        File tempFile = File.createTempFile("jcollections-compact", ".tmp", originalFile.getParentFile());

        // Save settings before closing to avoid accessing closed object
        boolean hasChecksums = entryList.getHeader().hasFlag(
            org.flossware.jcollections.file.format.FileHeader.FLAG_CHECKSUMS_ENABLED);
        boolean hasMmap = entryList.getHeader().hasFlag(
            org.flossware.jcollections.file.format.FileHeader.FLAG_MMAP_ENABLED);

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

        // Replace original file with compacted temp file
        if (!originalFile.delete()) {
            tempFile.delete();
            throw new IOException("Failed to delete original file during compaction");
        }

        if (!tempFile.renameTo(originalFile)) {
            throw new IOException("Failed to rename temp file to original file during compaction");
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
    }

    public void flush() throws IOException {
        entryList.flush();
    }

    @Override
    public SequencedMap<K, V> reversed() {
        return new FileBackedMap<K, V>(entryList.reversed(), index, useBTreeIndex);
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
