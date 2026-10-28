package org.example.adapter.outbound.bitcask;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Database implements AutoCloseable{
    private final Path dbDir;
    private final FileChannel activeFileChannel;
    private final Map<String, Long> keyDir = new ConcurrentHashMap<>();

    public Database(String dbPath) throws IOException {
        this.dbDir = Paths.get(dbPath);

        Files.createDirectories(dbDir);
        Path activePath = dbDir.resolve("active.data");

        activeFileChannel = FileChannel.open(
                activePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public synchronized void set(Entry entry) throws IOException {
        byte[] data = serializeEntry(entry);
        long offset = activeFileChannel.size();

        append(data);

        keyDir.put(entry.getKey(), offset);
    }


    private void append(byte[] data) throws IOException {
        writeAt(data, activeFileChannel.size());
    }

    private void writeAt(byte[] data, long offset) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        long position = offset;
        while (buffer.hasRemaining()) {
            int written = activeFileChannel.write(buffer, position);

            if (written == 0) {
                Thread.onSpinWait();
                continue;
            }

            position += written;
        }
    }


    public Entry get(String key) throws IOException {
        long offset = keyDir.get(key);

        long timestamp = readLong(offset);
        offset += Long.BYTES;

        int keySize = readInt(offset);
        offset += Integer.BYTES;

        int valueSize = readInt(offset);
        offset += Integer.BYTES;

        byte[] keyBytes = readBytes(offset, keySize);
        offset += keySize;

        byte[] valueBytes = readBytes(offset, valueSize);

        return new Entry(
                timestamp,
                new String(keyBytes, StandardCharsets.UTF_8),
                new String(valueBytes, StandardCharsets.UTF_8)
        );
    }

    public Map<String, Entry>getAll() throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        for(String key : keyDir.keySet())
            entries.put(key, get(key));

        return entries;
    }


    private ByteBuffer read(long offset, int length) throws IOException {
        if (offset < 0 || length < 0)
            throw new IllegalArgumentException("Offset and length must be non-negative");

        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            int bytesRead = activeFileChannel.read(buffer, offset);

            if (bytesRead == -1) {
                throw new EOFException("Not enough bytes in the file");
            }

            offset += bytesRead;
        }

        buffer.flip();
        return buffer;
    }

    private byte[] readBytes(long offset, int length) throws IOException {
        ByteBuffer buffer = read(offset, length);

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return bytes;
    }
    private long readLong(long offset) throws IOException {
        return read(offset, Long.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getLong();
    }
    private int readInt(long offset) throws IOException {
        return read(offset, Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();
    }
    private byte[] serializeEntry(Entry entry) {
        // <timestamp><key-size><value-size><key><value>
        byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);

        int totalSize =
                keyBytes.length +
                valueBytes.length +
                Long.BYTES +
                (Integer.BYTES * 2);

        ByteBuffer byteBuffer = ByteBuffer.allocate(totalSize);
        return byteBuffer
                .putLong(entry.getTimestamp())
                .putInt(keyBytes.length)
                .putInt(valueBytes.length)
                .put(keyBytes)
                .put(valueBytes)
                .array();
    }

    @Override
    public void close() throws IOException {
        activeFileChannel.close();
    }
}
