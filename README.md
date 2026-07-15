# collections-java

File-backed Java collections that persist data to disk, implementing Java 21's `SequencedCollection`, `SequencedMap`, and `SequencedSet` interfaces.

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.java.net/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-423%20passing-success.svg)]()
[![Coverage](https://img.shields.io/badge/coverage-95%25%20instruction%20%2F%2087%25%20branch-green.svg)]()

## Overview

collections-java provides persistent collection implementations backed by files on disk. Data survives process restarts. Collections are append-only, thread-safe, and support memory-mapped I/O, B-tree indexing, CRC32 checksums, write-behind caching, and compaction.

## Installation

```xml
<repositories>
    <repository>
        <id>packagecloud-flossware</id>
        <url>https://packagecloud.io/flossware/java/maven2</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.flossware</groupId>
        <artifactId>collections-java</artifactId>
        <version>1.0</version>
    </dependency>
</dependencies>
```

## Quick Start

### FileBackedList

```java
try (FileBackedList<String> list = new FileBackedList.Builder<String>(new File("data.bin"))
        .enableChecksums(true)
        .enableMmap(true)
        .enableCache(true)
        .build()) {

    list.add("Hello");
    list.add("World");

    list.forEach(System.out::println);
    list.reversed().forEach(System.out::println);

    System.out.println(list.getFirst());
    System.out.println(list.getLast());

    list.flush();
}
```

### FileBackedMap

```java
try (FileBackedMap<String, String> map =
        new FileBackedMap.Builder<String, String>(new File("map.bin"))
            .enableBTreeIndex(true)
            .enableChecksums(true)
            .build()) {

    map.put("key1", "value1");
    map.put("key2", "value2");

    String value = map.get("key1"); // O(log n) with B-tree

    map.compact(); // remove superseded duplicate entries
}
```

### FileBackedSet

```java
try (FileBackedSet<String> set = new FileBackedSet.Builder<String>(new File("set.bin"))
        .enableBTreeIndex(true)
        .build()) {

    set.add("Apple");
    set.add("Banana");
    set.add("Apple"); // no-op, already present

    set.forEach(System.out::println);
}
```

### Primitive Lists (no boxing)

```java
// IntList - native int storage (4 bytes per element)
try (IntList ints = new IntList(new File("ints.bin"))) {
    for (int i = 0; i < 1_000_000; i++) {
        ints.add(i);
    }
    int value = ints.get(500_000); // native int, no boxing
}

// LongList - native long storage (8 bytes per element)
try (LongList longs = new LongList(new File("longs.bin"))) {
    longs.add(Long.MAX_VALUE);
    long value = longs.get(0);
}

// DoubleList - native double storage (8 bytes per element)
try (DoubleList doubles = new DoubleList(new File("doubles.bin"))) {
    doubles.add(3.14159);
    double value = doubles.get(0);
}
```

### Custom Serializer

```java
// Use a custom serializer instead of Java's built-in ObjectOutputStream
Serializer<String> utf8 = new Serializer<>() {
    public byte[] serialize(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    public String deserialize(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
};

try (FileBackedList<String> list = new FileBackedList.Builder<String>(new File("data.bin"))
        .serializer(utf8)
        .build()) {
    list.add("Hello");
}
```

## Configuration

All generic collections use a Builder pattern:

| Option | Default | Description |
|--------|---------|-------------|
| `enableChecksums` | `true` | CRC32 checksums per entry for corruption detection |
| `enableMmap` | `true` | Memory-mapped I/O via `MappedByteBuffer` |
| `enableCache` | `true` | Write-behind cache to batch disk writes |
| `enableBTreeIndex` | `true` | In-memory B-tree index for O(log n) key lookups (maps only) |
| `cacheSize` | `1000` | Max cached entries before auto-flush |
| `cacheFlushMs` | `5000` | Time-based auto-flush interval (ms) |
| `enableFsync` | `false` | Force disk sync after every write for durability |
| `serializer` | `JavaSerializer` | Custom `Serializer<T>` for element encoding |
| `sharedLock` | `false` | Use shared (read) file locks instead of exclusive |

## Architecture

### Collections

| Class | Implements | Description |
|-------|-----------|-------------|
| `FileBackedList<E>` | `List<E>`, `SequencedCollection<E>` | Append-only file-backed list |
| `FileBackedMap<K,V>` | `Map<K,V>`, `SequencedMap<K,V>` | File-backed map with B-tree index |
| `FileBackedSet<E>` | `Set<E>`, `SequencedSet<E>` | File-backed set (delegates to map) |
| `IntList` | `AutoCloseable` | Primitive `int` list, always memory-mapped |
| `LongList` | `AutoCloseable` | Primitive `long` list, always memory-mapped |
| `DoubleList` | `AutoCloseable` | Primitive `double` list, always memory-mapped |

### Supporting Components

| Class | Purpose |
|-------|---------|
| `Serializer<T>` | Pluggable serialization interface for custom encoding |
| `JavaSerializer<T>` | Default serializer using Java's ObjectOutputStream |
| `FileHeader` | 64-byte binary header with magic bytes (`JCOL`), version, flags, CRC32 |
| `EntryChecksum` | CRC32 checksum calculation and verification |
| `BTreeIndex<K>` | In-memory order-128 B-tree for key lookups |
| `WriteCache<T>` | LRU write-behind cache with time/size flush triggers |
| `FileLockManager` | Multi-process file locking via `java.nio.channels.FileLock` |
| `FileValidator` | Validates file integrity, reports errors and warnings |
| `FileRepairer` | Recovers valid entries from corrupted files |

### File Format

```
[Header - 64 bytes]
  Magic:     0x4A434F4C ("JCOL")
  Version:   4 bytes (currently 2)
  Timestamp: 8 bytes (creation time)
  Flags:     4 bytes (checksums | mmap | btree | compressed)
  Reserved:  36 bytes
  Checksum:  8 bytes (CRC32 of preceding 56 bytes)

[Entry 1]
  Length:    4 bytes (int)
  Checksum:  8 bytes (CRC32, if enabled)
  Data:     N bytes (Java-serialized object)

[Entry 2...]
```

### Dependency Graph

```
FileBackedSet
  --> FileBackedMap (delegation)
        --> FileBackedList (stores SimpleEntry<K,V> objects)
        --> BTreeIndex (in-memory key index)
              --> FileHeader
              --> EntryChecksum
              --> WriteCache
              --> FileLockManager

IntList / LongList / DoubleList
  --> FileHeader
  --> FileLockManager
```

## Design

**Append-only**: `add()` and `put()` always append to the end of the file. Duplicate keys in maps accumulate on disk; the in-memory B-tree index tracks the latest value. Call `compact()` to rewrite the file with only unique entries.

**Thread safety**: All collections use `ReentrantReadWriteLock` for safe concurrent access within a single JVM. `FileLockManager` provides cross-process locking via `java.nio.channels.FileLock`.

**Serialization**: Default uses Java's `ObjectOutputStream`/`ObjectInputStream` via `JavaSerializer`. Provide a custom `Serializer<T>` via `.serializer()` in the Builder for alternatives (JSON, Protobuf, etc). Elements must implement `Serializable` when using the default serializer. Map keys must also implement `Comparable`.

**Unsupported operations**: `addFirst()`, `removeFirst()`, and `remove()` throw `UnsupportedOperationException` because they would require rewriting the entire file.

## Good For

- Append-only logs and event stores
- Persistent caches and configuration data
- Small to medium datasets
- Multi-process applications needing shared file access
- Prototyping persistent data structures

## Not Recommended For

- Mission-critical data requiring ACID guarantees
- Frequent random updates or deletes
- Network-shared filesystems (file locking unreliable)
- Datasets approaching 2GB (memory-mapped file limit)

## Building

```bash
git clone https://github.com/FlossWare/collections-java.git
cd collections-java
mvn clean verify
```

Requires Java 21+.

## License

[GNU General Public License v3.0](LICENSE)
