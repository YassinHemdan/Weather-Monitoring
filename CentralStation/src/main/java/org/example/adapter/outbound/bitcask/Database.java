package org.example.adapter.outbound.bitcask;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



public class Database implements AutoCloseable{
    private final Path dbDir;
    private final RandomAccessFile activeFile;
    private final Map<String, Long> keyDir = new HashMap<>();

    public Database(String dbPath) throws IOException {
        this.dbDir = Paths.get(dbPath);

        Files.createDirectories(dbDir);

        Path activePath = dbDir.resolve("active.data");
        this.activeFile = new RandomAccessFile(activePath.toFile(), "rw");
    }

    public void set(Entry entry) throws IOException {
        byte[] data = serializeEntry(entry);

        activeFile.seek(activeFile.length());

        keyDir.put(entry.getKey(), activeFile.length());

        activeFile.write(data);
    }

    public Entry get(String key) throws IOException {
        seek(key);

        // fixed positions
        long timestamp = activeFile.readLong();
        int keySize = activeFile.readInt();
        int valueSize = activeFile.readInt();

        byte[] keyBytes = new byte[keySize];
        byte[] valueBytes = new byte[valueSize];

        activeFile.readFully(keyBytes);
        activeFile.readFully(valueBytes);

        return new Entry(
                timestamp,
                new String(keyBytes, StandardCharsets.UTF_8),
                new String(valueBytes, StandardCharsets.UTF_8)
        );
    }

    public List<Pair<String, Entry>> getAll() throws IOException {
        List<Pair<String, Entry>> keyRecordslist = new ArrayList<>();
        for(String key : keyDir.keySet())
            keyRecordslist.add(new Pair<>(key, get(key)));

        return keyRecordslist;
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

    private void seek(String key) throws IOException {
        this.activeFile.seek(this.keyDir.get(key));
    }
    @Override
    public void close() throws IOException {
        activeFile.close();
    }
}
