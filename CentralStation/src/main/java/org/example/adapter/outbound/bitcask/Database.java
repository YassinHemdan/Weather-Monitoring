package org.example.adapter.outbound.bitcask;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class Database implements AutoCloseable{
    private record SerializedEntry(byte []bytes, int valueSize){}

    private static final int ENTRY_HEADER_SIZE = Long.BYTES + (Integer.BYTES * 2);

    private final SegmentsManager segmentsManager;
    private final Map<String, ValueLocation> keyDir = new ConcurrentHashMap<>();


    public Database(String dbPath) throws IOException {
        this(dbPath, SegmentsManager.DEFAULT_MAX_FILE_SIZE);
    }

    public Database(String dbPath, long maxSegmentSize) throws IOException {
        Path dbDir = Paths.get(Objects.requireNonNull(dbPath, "dbPath"));
        this.segmentsManager = new SegmentsManager(dbDir, maxSegmentSize);
    }


    public synchronized void set(Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        String key = Objects.requireNonNull(entry.value(), "entry.key");
        Objects.requireNonNull(entry.value(), "entry.value");

        SerializedEntry serialized = serializeEntry(entry);

        long endOffset = segmentsManager.write(serialized.bytes());

        long valuePos = endOffset - serialized.valueSize();

        String fileId = segmentsManager.activeFileId();

        keyDir.put(
                key,
                new ValueLocation(
                        fileId,
                        serialized.valueSize(),
                        valuePos,
                        entry.timestamp()
                )
        );
    }

    public Optional<Entry> find(String key) throws IOException {
        Objects.requireNonNull(key, "key");

        ValueLocation location = keyDir.get(key);
        if (location == null) {
            return Optional.empty();
        }

        byte[] valueBytes = segmentsManager.read(location.valuePos(), location.fileId(), location.valueSize());
        return Optional.of(new Entry(
                location.timestamp(),
                key,
                new String(valueBytes, StandardCharsets.UTF_8)
        ));
    }

    public Entry get(String key) throws IOException {
        return find(key).orElseThrow(() -> new KeyNotFoundException(key));
    }

    public Map<String, Entry> getAll() throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        for (String key : keyDir.keySet()) {
            find(key).ifPresent(entry -> entries.put(key, entry));
        }
        return entries;
    }
    private SerializedEntry serializeEntry(Entry entry) {
        // <timestamp><key-size><value-size><key><value>
        byte[] keyBytes = entry.key().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = entry.value().getBytes(StandardCharsets.UTF_8);
        int totalSize = ENTRY_HEADER_SIZE + keyBytes.length + valueBytes.length;

        byte[] bytes = ByteBuffer.allocate(totalSize)
                .putLong(entry.timestamp())
                .putInt(keyBytes.length)
                .putInt(valueBytes.length)
                .put(keyBytes)
                .put(valueBytes)
                .array();

        return new SerializedEntry(bytes, valueBytes.length);
    }

    @Override
    public void close() throws IOException {
        segmentsManager.close();
    }
}
