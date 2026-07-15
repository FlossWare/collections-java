package org.flossware.collections.file.format;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class FileHeader {
    private static final int MAGIC_BYTES = 0x4A434F4C; // "JCOL" in hex
    private static final int HEADER_SIZE = 64;

    private final int version;
    private final long creationTime;
    private final int flags;
    private final long checksum;

    public static final int VERSION_1 = 1;
    public static final int VERSION_2 = 2;

    public static final int FLAG_CHECKSUMS_ENABLED = 0x01;
    public static final int FLAG_MMAP_ENABLED = 0x02;
    public static final int FLAG_BTREE_INDEX = 0x04;
    public static final int FLAG_COMPRESSED = 0x08;
    public static final int FLAG_FSYNC_ENABLED = 0x10;

    public FileHeader(int version, int flags) {
        this.version = version;
        this.creationTime = System.currentTimeMillis();
        this.flags = flags;
        this.checksum = 0;
    }

    private FileHeader(int version, long creationTime, int flags, long checksum) {
        this.version = version;
        this.creationTime = creationTime;
        this.flags = flags;
        this.checksum = checksum;
    }

    public void write(RandomAccessFile file) throws IOException {
        file.seek(0);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);

        buffer.putInt(MAGIC_BYTES);
        buffer.putInt(version);
        buffer.putLong(creationTime);
        buffer.putInt(flags);

        int headerDataSize = 4 + 4 + 8 + 4;
        for (int i = headerDataSize; i < HEADER_SIZE - 8; i++) {
            buffer.put((byte) 0);
        }

        CRC32 crc = new CRC32();
        crc.update(buffer.array(), 0, HEADER_SIZE - 8);
        buffer.putLong(crc.getValue());

        buffer.flip();
        file.write(buffer.array());
    }

    public static FileHeader read(RandomAccessFile file) throws IOException {
        if (file.length() < HEADER_SIZE) {
            return null;
        }

        file.seek(0);
        byte[] headerBytes = new byte[HEADER_SIZE];
        file.readFully(headerBytes);

        ByteBuffer buffer = ByteBuffer.wrap(headerBytes);

        int magic = buffer.getInt();
        if (magic != MAGIC_BYTES) {
            return null;
        }

        int version = buffer.getInt();
        long creationTime = buffer.getLong();
        int flags = buffer.getInt();

        buffer.position(HEADER_SIZE - 8);
        long storedChecksum = buffer.getLong();

        CRC32 crc = new CRC32();
        crc.update(headerBytes, 0, HEADER_SIZE - 8);

        if (crc.getValue() != storedChecksum) {
            throw new IOException("Header checksum mismatch");
        }

        return new FileHeader(version, creationTime, flags, storedChecksum);
    }

    public int getVersion() {
        return version;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public int getFlags() {
        return flags;
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }

    public static int getHeaderSize() {
        return HEADER_SIZE;
    }
}
