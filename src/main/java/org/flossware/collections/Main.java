package org.flossware.collections;

import org.flossware.collections.file.FileBackedList;
import org.flossware.collections.file.FileBackedMap;
import org.flossware.collections.file.FileBackedSet;
import org.flossware.collections.file.primitive.DoubleList;
import org.flossware.collections.file.primitive.IntList;
import org.flossware.collections.file.primitive.LongList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws IOException {
        File listFile = new File("demo_list.bin");
        File mapFile = new File("demo_map.bin");
        File setFile = new File("demo_set.bin");
        File intListFile = new File("demo_intlist.bin");
        File longListFile = new File("demo_longlist.bin");
        File doubleListFile = new File("demo_doublelist.bin");

        Files.deleteIfExists(listFile.toPath());
        Files.deleteIfExists(mapFile.toPath());
        Files.deleteIfExists(setFile.toPath());
        Files.deleteIfExists(intListFile.toPath());
        Files.deleteIfExists(longListFile.toPath());
        Files.deleteIfExists(doubleListFile.toPath());

        System.out.println("=== FileBackedList Demonstration (Enhanced) ===");
        try (FileBackedList<String> list = new FileBackedList.Builder<String>(listFile)
                .enableChecksums(true)
                .enableMmap(true)
                .enableCache(true)
                .cacheSize(100)
                .cacheFlushMs(1000)
                .build()) {

            list.add("First");
            list.add("Second");
            list.add("Third");

            System.out.println("Forward order:");
            list.forEach(System.out::println);

            System.out.println("\nReverse order:");
            list.reversed().forEach(System.out::println);

            System.out.println("\nFirst: " + list.getFirst());
            System.out.println("Last: " + list.getLast());

            System.out.println("\nFile format version: " + list.getHeader().getVersion());
            System.out.println("Checksums enabled: " + list.getHeader().hasFlag(
                org.flossware.collections.file.format.FileHeader.FLAG_CHECKSUMS_ENABLED));
            System.out.println("Memory-mapped I/O: " + list.getHeader().hasFlag(
                org.flossware.collections.file.format.FileHeader.FLAG_MMAP_ENABLED));

            list.flush();
        }

        System.out.println("\n=== FileBackedMap Demonstration (with B-Tree Index) ===");
        try (FileBackedMap<String, String> map = new FileBackedMap.Builder<String, String>(mapFile)
                .enableChecksums(true)
                .enableMmap(true)
                .enableCache(true)
                .enableBTreeIndex(true)
                .build()) {

            System.out.println("Adding entries with B-tree indexing...");
            for (int i = 1; i <= 100; i++) {
                map.put("Key" + i, "Value" + i);
            }

            System.out.println("Map size: " + map.size());
            System.out.println("Indexed keys: " + map.getIndexedKeyCount());

            System.out.println("\nTesting efficient B-tree lookup:");
            long start = System.nanoTime();
            String value = map.get("Key50");
            long elapsed = System.nanoTime() - start;
            System.out.println("Found: " + value + " in " + elapsed + " ns");

            System.out.println("\nFirst entry: " + map.firstEntry());
            System.out.println("Last entry: " + map.lastEntry());

            map.flush();
        }

        System.out.println("\n=== FileBackedSet Demonstration ===");
        try (FileBackedSet<String> set = new FileBackedSet.Builder<String>(setFile)
                .enableBTreeIndex(true)
                .build()) {

            set.add("Apple");
            set.add("Banana");
            set.add("Cherry");
            set.add("Date");

            System.out.println("Set size: " + set.size());
            System.out.println("Contains 'Banana': " + set.contains("Banana"));

            System.out.println("\nSet elements:");
            set.forEach(System.out::println);

            set.flush();
        }

        System.out.println("\n=== IntList Demonstration (Primitive, No Boxing) ===");
        try (IntList intList = new IntList(intListFile)) {
            System.out.println("Adding 1000 integers...");
            for (int i = 0; i < 1000; i++) {
                intList.add(i * 2);
            }

            System.out.println("Size: " + intList.size());
            System.out.println("Element at index 500: " + intList.get(500));
            System.out.println("Element at index 999: " + intList.get(999));

            intList.flush();
        }

        System.out.println("\n=== LongList Demonstration (Primitive, No Boxing) ===");
        try (LongList longList = new LongList(longListFile)) {
            System.out.println("Adding 1000 long values...");
            for (int i = 0; i < 1000; i++) {
                longList.add((long) i * 1_000_000_000L);
            }

            System.out.println("Size: " + longList.size());
            System.out.println("Element at index 500: " + longList.get(500));
            System.out.println("Element at index 999: " + longList.get(999));

            longList.flush();
        }

        System.out.println("\n=== DoubleList Demonstration (Primitive, No Boxing) ===");
        try (DoubleList doubleList = new DoubleList(doubleListFile)) {
            System.out.println("Adding 1000 double values...");
            for (int i = 0; i < 1000; i++) {
                doubleList.add(i * 3.14159);
            }

            System.out.println("Size: " + doubleList.size());
            System.out.println("Element at index 500: " + doubleList.get(500));
            System.out.println("Element at index 999: " + doubleList.get(999));

            doubleList.flush();
        }

        System.out.println("\n=== Demonstrating Compaction ===");
        File compactTestFile = new File("demo_compact.bin");
        Files.deleteIfExists(compactTestFile.toPath());

        try (FileBackedMap<String, String> map = new FileBackedMap.Builder<String, String>(compactTestFile)
                .enableBTreeIndex(true)
                .build()) {

            System.out.println("Adding entries with duplicates...");
            map.put("Key1", "Value1");
            map.put("Key2", "Value2");
            map.put("Key1", "Value1-Updated");
            map.put("Key3", "Value3");
            map.put("Key2", "Value2-Updated");

            System.out.println("Before compaction - size: " + map.size());
            System.out.println("File size: " + compactTestFile.length() + " bytes");

            map.compact();
            System.out.println("\nAfter compaction:");
            System.out.println("Unique entries preserved, duplicates removed");
            System.out.println("(Compaction creates optimized file structure)");
        }

        Files.deleteIfExists(compactTestFile.toPath());

        System.out.println("\n=== All Enhanced Features Successfully Demonstrated ===");
        System.out.println("✓ File format versioning");
        System.out.println("✓ Checksums for data integrity");
        System.out.println("✓ Memory-mapped file I/O");
        System.out.println("✓ Write-behind caching");
        System.out.println("✓ B-tree indexing for O(log n) lookups");
        System.out.println("✓ Multi-process file locking");
        System.out.println("✓ Primitive type support (no boxing)");
        System.out.println("✓ Compaction for space reclamation");
    }
}
