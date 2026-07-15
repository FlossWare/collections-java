package org.flossware.collections.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;

/**
 * Default {@link Serializer} implementation that uses Java's built-in
 * {@link ObjectOutputStream}/{@link ObjectInputStream} serialization.
 *
 * <p>This preserves the original behavior of {@link FileBackedList} for
 * backward compatibility. Objects must implement {@link Serializable}.</p>
 *
 * @param <T> the type of objects to serialize/deserialize
 */
public class JavaSerializer<T extends Serializable> implements Serializer<T> {

    @Override
    public byte[] serialize(T object) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(object);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize object", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(byte[] data) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) ois.readObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize object", e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize object: class not found", e);
        }
    }
}
