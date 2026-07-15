package org.flossware.collections.file;

/**
 * Strategy interface for serializing and deserializing objects to/from byte arrays.
 *
 * <p>This allows pluggable serialization formats (JSON, Protobuf, etc.) instead of
 * being locked into Java's {@link java.io.ObjectOutputStream}/{@link java.io.ObjectInputStream}.</p>
 *
 * <p>The default implementation, {@link JavaSerializer}, preserves backward compatibility
 * by wrapping the existing Java serialization behavior.</p>
 *
 * @param <T> the type of objects to serialize/deserialize
 */
public interface Serializer<T> {
    /**
     * Serializes the given object into a byte array.
     *
     * @param object the object to serialize
     * @return the serialized byte representation
     */
    byte[] serialize(T object);

    /**
     * Deserializes a byte array back into an object.
     *
     * @param data the byte array to deserialize
     * @return the deserialized object
     */
    T deserialize(byte[] data);
}
