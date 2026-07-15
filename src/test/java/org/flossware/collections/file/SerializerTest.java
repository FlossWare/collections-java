package org.flossware.collections.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerializerTest {
    @TempDir
    Path tempDir;

    private File testFile;
    private FileBackedList<String> list;

    @AfterEach
    void tearDown() throws IOException {
        if (list != null) {
            list.close();
        }
    }

    // --- JavaSerializer unit tests ---

    @Test
    void testJavaSerializerRoundTrip() {
        JavaSerializer<String> serializer = new JavaSerializer<>();
        String original = "Hello, World!";
        byte[] data = serializer.serialize(original);
        assertNotNull(data);

        String result = serializer.deserialize(data);
        assertEquals(original, result);
    }

    @Test
    void testJavaSerializerWithInteger() {
        JavaSerializer<Integer> serializer = new JavaSerializer<>();
        Integer original = 42;
        byte[] data = serializer.serialize(original);
        Integer result = serializer.deserialize(data);
        assertEquals(original, result);
    }

    @Test
    void testJavaSerializerWithComplexObject() {
        JavaSerializer<TestRecord> serializer = new JavaSerializer<>();
        TestRecord original = new TestRecord("test", 123);
        byte[] data = serializer.serialize(original);
        TestRecord result = serializer.deserialize(data);
        assertEquals(original, result);
    }

    @Test
    void testJavaSerializerDeserializeInvalidData() {
        JavaSerializer<String> serializer = new JavaSerializer<>();
        byte[] badData = new byte[]{0, 1, 2, 3};
        assertThrows(UncheckedIOException.class, () -> serializer.deserialize(badData));
    }

    // --- FileBackedList with default JavaSerializer (backward compatibility) ---

    @Test
    void testDefaultSerializerBackwardCompatibility() throws IOException {
        testFile = tempDir.resolve("default-serializer.bin").toFile();
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .build();

        list.add("First");
        list.add("Second");
        list.add("Third");

        assertEquals(3, list.size());
        assertEquals("First", list.get(0));
        assertEquals("Second", list.get(1));
        assertEquals("Third", list.get(2));
    }

    @Test
    void testDefaultSerializerPersistence() throws IOException {
        testFile = tempDir.resolve("default-persist.bin").toFile();
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .build();

        list.add("Persist1");
        list.add("Persist2");
        list.flush();
        list.close();

        // Reopen and verify data persisted
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .build();

        assertEquals(2, list.size());
        assertEquals("Persist1", list.get(0));
        assertEquals("Persist2", list.get(1));
    }

    // --- FileBackedList with custom Serializer ---

    @Test
    void testCustomSerializerAddAndGet() throws IOException {
        testFile = tempDir.resolve("custom-serializer.bin").toFile();
        Serializer<String> utf8Serializer = new Utf8StringSerializer();

        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .serializer(utf8Serializer)
            .build();

        list.add("Hello");
        list.add("World");

        assertEquals(2, list.size());
        assertEquals("Hello", list.get(0));
        assertEquals("World", list.get(1));
    }

    @Test
    void testCustomSerializerPersistence() throws IOException {
        testFile = tempDir.resolve("custom-persist.bin").toFile();
        Serializer<String> utf8Serializer = new Utf8StringSerializer();

        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .serializer(utf8Serializer)
            .build();

        list.add("Alpha");
        list.add("Beta");
        list.add("Gamma");
        list.flush();
        list.close();

        // Reopen with same custom serializer
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(false)
            .serializer(utf8Serializer)
            .build();

        assertEquals(3, list.size());
        assertEquals("Alpha", list.get(0));
        assertEquals("Beta", list.get(1));
        assertEquals("Gamma", list.get(2));
    }

    @Test
    void testCustomSerializerWithMmap() throws IOException {
        testFile = tempDir.resolve("custom-mmap.bin").toFile();
        Serializer<String> utf8Serializer = new Utf8StringSerializer();

        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(true)
            .enableCache(false)
            .serializer(utf8Serializer)
            .build();

        list.add("MmapTest1");
        list.add("MmapTest2");

        assertEquals(2, list.size());
        assertEquals("MmapTest1", list.get(0));
        assertEquals("MmapTest2", list.get(1));
    }

    @Test
    void testCustomSerializerWithCache() throws IOException {
        testFile = tempDir.resolve("custom-cache.bin").toFile();
        Serializer<String> utf8Serializer = new Utf8StringSerializer();

        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(true)
            .enableMmap(false)
            .enableCache(true)
            .cacheSize(100)
            .serializer(utf8Serializer)
            .build();

        list.add("Cached1");
        list.add("Cached2");

        assertEquals(2, list.size());
        assertEquals("Cached1", list.get(0));
        assertEquals("Cached2", list.get(1));
    }

    @Test
    void testCustomSerializerProducesSmallerOutput() throws IOException {
        Serializer<String> utf8Serializer = new Utf8StringSerializer();
        JavaSerializer<String> javaSerializer = new JavaSerializer<>();

        String testValue = "Hello";
        byte[] utf8Bytes = utf8Serializer.serialize(testValue);
        byte[] javaBytes = javaSerializer.serialize(testValue);

        // UTF-8 encoding is much more compact than Java serialization
        assertEquals(5, utf8Bytes.length); // "Hello" is 5 bytes in UTF-8
        // Java serialization adds significant overhead (header, type info, etc.)
        // Just verify it's larger, not the exact size
        assertEquals(true, javaBytes.length > utf8Bytes.length);
    }

    // --- Builder validation tests ---

    @Test
    void testBuilderRejectsNullSerializer() {
        testFile = tempDir.resolve("null-serializer.bin").toFile();
        assertThrows(IllegalArgumentException.class, () ->
            new FileBackedList.Builder<String>(testFile).serializer(null)
        );
    }

    @Test
    void testBuilderSerializerIsChainable() throws IOException {
        testFile = tempDir.resolve("chain.bin").toFile();
        Serializer<String> utf8Serializer = new Utf8StringSerializer();

        // Verify the builder pattern chains correctly
        list = new FileBackedList.Builder<String>(testFile)
            .enableChecksums(false)
            .enableMmap(false)
            .enableCache(false)
            .serializer(utf8Serializer)
            .build();

        assertNotNull(list);
        list.add("chained");
        assertEquals("chained", list.get(0));
    }

    // --- Custom Serializer implementations for testing ---

    /**
     * A simple String serializer using UTF-8 encoding.
     * Demonstrates a custom Serializer that produces much smaller output
     * than Java's ObjectOutputStream.
     */
    private static class Utf8StringSerializer implements Serializer<String> {
        @Override
        public byte[] serialize(String object) {
            return object.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String deserialize(byte[] data) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /**
     * Simple serializable record for testing complex object serialization.
     */
    private record TestRecord(String name, int value) implements Serializable {
    }
}
