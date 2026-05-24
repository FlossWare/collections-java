package org.flossware.jcollections.file.index;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BTreeIndex<K extends Serializable & Comparable<K>> {
    private static final int ORDER = 128;
    private BTreeNode<K> root;
    private final ReentrantReadWriteLock lock;

    public static class IndexEntry<K> {
        public final K key;
        public final long offset;

        public IndexEntry(K key, long offset) {
            this.key = key;
            this.offset = offset;
        }
    }

    private static class BTreeNode<K extends Comparable<K>> {
        List<IndexEntry<K>> entries;
        List<BTreeNode<K>> children;
        boolean isLeaf;

        BTreeNode(boolean isLeaf) {
            this.entries = new ArrayList<>(ORDER);
            this.children = isLeaf ? null : new ArrayList<>(ORDER + 1);
            this.isLeaf = isLeaf;
        }
    }

    public BTreeIndex() {
        this.root = new BTreeNode<>(true);
        this.lock = new ReentrantReadWriteLock();
    }

    public Long get(K key) {
        lock.readLock().lock();
        try {
            return search(root, key);
        } finally {
            lock.readLock().unlock();
        }
    }

    private Long search(BTreeNode<K> node, K key) {
        if (node == null) {
            return null;
        }

        int i = 0;
        while (i < node.entries.size() && key.compareTo(node.entries.get(i).key) > 0) {
            i++;
        }

        if (i < node.entries.size() && key.compareTo(node.entries.get(i).key) == 0) {
            return node.entries.get(i).offset;
        }

        if (node.isLeaf) {
            return null;
        }

        return search(node.children.get(i), key);
    }

    public void put(K key, long offset) {
        lock.writeLock().lock();
        try {
            IndexEntry<K> entry = new IndexEntry<>(key, offset);

            if (root.entries.size() == ORDER - 1) {
                BTreeNode<K> oldRoot = root;
                root = new BTreeNode<>(false);
                root.children = new ArrayList<>();
                root.children.add(oldRoot);
                splitChild(root, 0);
            }

            insertNonFull(root, entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void insertNonFull(BTreeNode<K> node, IndexEntry<K> entry) {
        int i = node.entries.size() - 1;

        if (node.isLeaf) {
            // Find the insertion position
            while (i >= 0 && entry.key.compareTo(node.entries.get(i).key) < 0) {
                i--;
            }
            // Insert at the correct position (i+1)
            node.entries.add(i + 1, entry);
        } else {
            while (i >= 0 && entry.key.compareTo(node.entries.get(i).key) < 0) {
                i--;
            }
            i++;

            if (node.children.get(i).entries.size() == ORDER - 1) {
                splitChild(node, i);
                if (entry.key.compareTo(node.entries.get(i).key) > 0) {
                    i++;
                }
            }
            insertNonFull(node.children.get(i), entry);
        }
    }

    private void splitChild(BTreeNode<K> parent, int index) {
        BTreeNode<K> fullChild = parent.children.get(index);
        BTreeNode<K> newChild = new BTreeNode<>(fullChild.isLeaf);

        int mid = ORDER / 2;

        newChild.entries.addAll(fullChild.entries.subList(mid, fullChild.entries.size()));
        fullChild.entries.subList(mid, fullChild.entries.size()).clear();

        if (!fullChild.isLeaf) {
            newChild.children.addAll(fullChild.children.subList(mid, fullChild.children.size()));
            fullChild.children.subList(mid, fullChild.children.size()).clear();
        }

        parent.entries.add(index, fullChild.entries.remove(mid - 1));
        parent.children.add(index + 1, newChild);
    }

    public List<IndexEntry<K>> getAll() {
        lock.readLock().lock();
        try {
            List<IndexEntry<K>> result = new ArrayList<>();
            collectAll(root, result);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void collectAll(BTreeNode<K> node, List<IndexEntry<K>> result) {
        if (node == null) {
            return;
        }

        if (node.isLeaf) {
            result.addAll(node.entries);
        } else {
            for (int i = 0; i < node.entries.size(); i++) {
                collectAll(node.children.get(i), result);
                result.add(node.entries.get(i));
            }
            collectAll(node.children.get(node.entries.size()), result);
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            root = new BTreeNode<>(true);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
