package org.flossware.jcollections.file;

import org.flossware.jcollections.file.index.BTreeIndex;

import java.io.*;
import java.util.*;

public class FileBackedMap<K extends Serializable & Comparable<K>, V extends Serializable> extends AbstractMap<K, V> implements SequencedMap<K, V>, AutoCloseable {
    private final FileBackedList<AbstractMap.SimpleEntry<K, V>> entryList;
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

        V result = null;
        for (Map.Entry<K, V> entry : entryList) {
            if (Objects.equals(entry.getKey(), key)) {
                result = entry.getValue();
            }
        }
        return result;
    }

    @Override
    public boolean containsKey(Object key) {
        if (useBTreeIndex && index != null && key instanceof Comparable) {
            @SuppressWarnings("unchecked")
            K k = (K) key;
            return index.get(k) != null;
        }

        for (Map.Entry<K, V> entry : entryList) {
            if (Objects.equals(entry.getKey(), key)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public V put(K key, V value) {
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

        File tempFile = File.createTempFile("jcollections-compact", ".tmp");
        try (FileBackedMap<K, V> tempMap = new Builder<K, V>(tempFile)
                .enableChecksums(entryList.getHeader().hasFlag(org.flossware.jcollections.file.format.FileHeader.FLAG_CHECKSUMS_ENABLED))
                .enableMmap(entryList.getHeader().hasFlag(org.flossware.jcollections.file.format.FileHeader.FLAG_MMAP_ENABLED))
                .enableBTreeIndex(useBTreeIndex)
                .build()) {

            for (Map.Entry<K, V> entry : uniqueEntries.entrySet()) {
                tempMap.put(entry.getKey(), entry.getValue());
            }
            tempMap.flush();
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
