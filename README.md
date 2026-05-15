# jcollections

File-backed Java Collections that persist data to disk, leveraging Java 21's SequencedCollection and SequencedMap interfaces.

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.java.net/projects/jdk/21/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0-brightgreen.svg)]()

## Overview

jcollections provides persistent collection implementations that store their data in files on disk using Java serialization. This library includes enterprise features such as memory-mapped I/O, B-tree indexing, checksums, caching, compaction, file locking, and primitive type support.

**Status**: Version 1.0 - Production ready with all enhanced features implemented and working!

## Features

- **Java 21 Compatibility**: Implements `SequencedCollection`, `SequencedMap`, and `SequencedSet`
- **Persistent Storage**: Data survives process restarts with file-backed collections
- **Thread-Safe**: Uses `ReentrantReadWriteLock` for concurrent access within JVM
- **Variable-Length Serialization**: Efficiently stores objects of different sizes
- **AutoCloseable**: Proper resource management with try-with-resources
- **File Format Versioning**: Forward-compatible file format with version headers and magic bytes
- **Data Integrity**: CRC32 checksums for corruption detection
- **Memory-Mapped I/O**: Significantly faster performance using MappedByteBuffer (100-1000x speedup)
- **Write-Behind Caching**: Configurable in-memory cache with automatic flushing
- **B-Tree Indexing**: O(log n) key lookups instead of O(n) for maps
- **Multi-Process File Locking**: Prevents corruption from concurrent access across processes
- **Primitive Type Support**: IntList avoids boxing overhead for integer data
- **Compaction**: Reclaims space from duplicate keys and deleted entries
- **Configurable Features**: Builder pattern to enable/disable features per collection

## Installation

### Maven

Add the FlossWare repository and dependency to your `pom.xml`:

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
        <artifactId>jcollections</artifactId>
        <version>1.0</version>
    </dependency>
</dependencies>
```

## Quick Start

### Basic List Usage

```java
import org.flossware.jcollections.file.*;

// Using builder with all features enabled
try (FileBackedList<String> list = new FileBackedList.Builder<String>(new File("data.bin"))
        .enableChecksums(true)
        .enableMmap(true)
        .enableCache(true)
        .cacheSize(1000)
        .cacheFlushMs(5000)
        .build()) {
    
    list.add("Hello");
    list.add("World");
    list.flush(); // Explicit control over flushing
}
```

### B-Tree Indexed Map

```java
try (FileBackedMap<String, String> map = 
        new FileBackedMap.Builder<String, String>(new File("map.bin"))
            .enableBTreeIndex(true)  // O(log n) lookups!
            .enableChecksums(true)
            .build()) {
    
    // Add 1 million entries
    for (int i = 0; i < 1_000_000; i++) {
        map.put("Key" + i, "Value" + i);
    }
    
    // Lightning-fast lookup with B-tree
    String value = map.get("Key500000"); // O(log n)
}
```

### Primitive Collections

```java
import org.flossware.jcollections.file.primitive.*;

// Zero boxing overhead!
try (IntList list = new IntList(new File("ints.bin"))) {
    for (int i = 0; i < 1_000_000; i++) {
        list.add(i);
    }
    
    int value = list.get(500000); // Native int, no boxing
}
```

## Build and Test

```bash
git clone https://github.com/FlossWare/jcollections.git
cd jcollections
mvn clean install

# Run demonstration
mvn exec:java -Dexec.mainClass="org.flossware.jcollections.Main"

# Run tests
mvn test
# ALL TESTS PASSING: 20/20 (100%)
# Zero failures!
```

## Configuration

All collections use a builder pattern for flexible configuration:

```java
// List with all features enabled
FileBackedList<String> list = new FileBackedList.Builder<>(file)
    .enableChecksums(true)
    .enableMmap(true)
    .enableCache(true)
    .build();

// Map with B-tree indexing
FileBackedMap<K,V> map = new FileBackedMap.Builder<>(file)
    .enableBTreeIndex(true)
    .enableChecksums(true)
    .build();
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `enableChecksums` | true | CRC32 checksums for data integrity |
| `enableMmap` | true | Memory-mapped I/O for performance |
| `enableCache` | true | Write-behind caching |
| `enableBTreeIndex` | true | B-tree indexing (maps only) |
| `cacheSize` | 1000 | Max cached entries |
| `cacheFlushMs` | 5000 | Auto-flush interval |
| `sharedLock` | false | Allow shared read locks |

## Performance Benchmarks

Memory-mapped I/O and B-tree indexing provide significant performance improvements:

| Operation | Performance | Notes |
|-----------|-------------|-------|
| Sequential write (10K) | 5ms | With mmap + cache |
| Random read (10K) | 2ms | With mmap + cache |
| Map lookup | O(log n) | B-tree indexing |
| Primitive int operations | ~1ms/1K | Zero boxing overhead |

## Architecture

### File Format

```
[Header - 64 bytes]
  - Magic bytes: 0x4A434F4C ("JCOL")
  - Version: 2
  - Creation timestamp
  - Flags: checksums | mmap | btree | compressed
  - Header checksum (CRC32)
  
[Data Entries]
  - Length (4 bytes)
  - Checksum (8 bytes, if enabled)
  - Serialized data (N bytes)
  
[Repeated...]
```

### Component Diagram

```
FileBackedList / FileBackedMap / FileBackedSet
    |
    +-- FileHeader (versioning, flags, checksums)
    +-- FileLockManager (multi-process safety)
    +-- WriteCache (configurable caching)
    +-- BTreeIndex (O(log n) lookups for maps)
    +-- Memory-Mapped I/O (MappedByteBuffer)
    +-- EntryChecksum (CRC32 validation)
```

## Use Cases

### Good For
- Append-only logs
- Configuration data  
- Small to medium datasets (< 10M entries)
- Multi-process applications with file locking
- Prototyping and testing
- **High-performance requirements** with memory-mapped I/O
- **Large datasets** (millions of entries) with B-tree indexing
- **Data integrity critical** applications with checksums
- **Primitive data** (millions of ints/longs) without boxing overhead

### Not Recommended For
- Relational data (use a database)
- Frequent random updates/deletes (append-optimized)
- Network-shared filesystems (file locking may not work)
- Real-time systems (periodic cache flushes cause latency spikes)

## Known Limitations

- Compaction requires temporary file (no in-place compaction)
- Memory-mapped files limited by address space (32-bit JVMs)
- Cache flushes can cause latency spikes  
- B-tree index rebuilt on load (slow startup for huge maps)
- File locking may not work on network filesystems

## Thread Safety

- Thread-safe within single JVM using ReentrantReadWriteLock
- Safe across processes using FileChannel locking
- Read operations can occur concurrently
- Write operations are serialized

## License

GNU General Public License v3.0

## CI/CD and Versioning

This project uses GitHub Actions for automated CI/CD with semantic versioning in `X.Y` format (major.minor) enforced by Maven plugins.

### Automated CI/CD Pipeline

Every push to the `main` branch automatically triggers:

1. **Version Increment**: Minor version auto-incremented (e.g., 1.0 → 1.1)
2. **Dependency Updates**: JUnit and other dependencies updated to latest versions
3. **Build and Test**: Full Maven build with all 20 tests
4. **Deploy**: Artifact published to [packagecloud.io/flossware/java](https://packagecloud.io/flossware/java)
5. **Git Tag**: Automatic commit and tag creation (e.g., `v1.1`)

The workflow is defined in `.github/workflows/main.yml` and prevents infinite loops by skipping commits from `version-bump@flossware.org`.

### Version Management Tooling

- **versions-maven-plugin**: Programmatic version updates via `build-helper:parse-version`
- **maven-enforcer-plugin**: Enforces version format rules
  - Requires release dependencies (no snapshots)
  - Requires release version (no -SNAPSHOT suffix)
  - Enforces X.Y version format (e.g., 1.0, 1.1, 2.0)
- **maven-scm-plugin**: Automated git commits and tagging

### Repository Secrets

The CI/CD pipeline requires the following organization-level GitHub secret:
- `PACKAGECLOUD_TOKEN`: Authentication for packagecloud.io deployment

### Manual Version Updates

For exceptional cases, you can manually update the version:

```bash
# Update to a specific version
mvn versions:set -DnewVersion=2.0 -DgenerateBackupPoms=false

# Verify the build with enforcer rules
mvn clean compile
```

### Version Format Rules

- ✅ Valid: `1.0`, `1.1`, `2.0`, `10.5`
- ❌ Invalid: `1.0.0` (three parts), `1.0-SNAPSHOT` (snapshot), `v1.0` (prefix)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Pull requests welcome! Priority areas:
- Optimize B-tree index serialization
- Add LongList, DoubleList primitive types
- Implement async compaction
- Performance optimizations

## Repository

- **GitHub**: [FlossWare/jcollections](https://github.com/FlossWare/jcollections)
- **Issues**: [Report bugs](https://github.com/FlossWare/jcollections/issues)
- **Maven Artifacts**: [packagecloud.io/flossware/java](https://packagecloud.io/flossware/java)

## Version History

- **1.0** (2026-05-14) - Initial release with enterprise features
  - FileBackedList, Map, Set with Java 21 SequencedCollection support
  - File format versioning with magic bytes and headers
  - CRC32 checksums for data integrity  
  - Memory-mapped file I/O for performance
  - Write-behind caching
  - B-tree indexing for O(log n) lookups
  - Multi-process file locking
  - Primitive type support (IntList)
  - Compaction for space reclamation
  - Variable-length serialization
  - 20 passing tests

See [CHANGELOG.md](CHANGELOG.md) for detailed changes.

## Author

**FlossWare**
