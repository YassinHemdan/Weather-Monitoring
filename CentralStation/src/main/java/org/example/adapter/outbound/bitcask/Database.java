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
    private static final int ENTRY_HEADER_SIZE = Long.BYTES + (Integer.BYTES * 2);

    private final SegmentsManager segmentsManager;
    private final Map<String, EntryLocation> keyDir = new ConcurrentHashMap<>();


    public Database(String dbPath) throws IOException {
        this(dbPath, SegmentsManager.DEFAULT_MAX_FILE_SIZE);
    }

    public Database(String dbPath, long maxSegmentSize) throws IOException {
        Path dbDir = Paths.get(Objects.requireNonNull(dbPath, "dbPath"));
        this.segmentsManager = new SegmentsManager(dbDir, maxSegmentSize);
    }


    public synchronized void set(Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        String key = Objects.requireNonNull(entry.getKey(), "entry.key");
        Objects.requireNonNull(entry.getValue(), "entry.value");

        byte[] data = serializeEntry(entry);
        EntryLocation location = segmentsManager.write(data);
        keyDir.put(entry.getKey(), location);
    }

    public Optional<Entry> find(String key) throws IOException {
        Objects.requireNonNull(key, "key");
        EntryLocation location = keyDir.get(key);
        if (location == null) {
            return Optional.empty();
        }

        byte[] data = segmentsManager.read(location.offset(), location.fileId(), location.entrySize());

        if (data.length < ENTRY_HEADER_SIZE) {
            throw new CorruptEntryException("Entry for key '" + key + "' is shorter than its header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        long timestamp = buffer.getLong();
        int keySize = buffer.getInt();
        int valueSize = buffer.getInt();

        if (keySize < 0 || valueSize < 0) {
            throw new CorruptEntryException("Entry for key '" + key + "' has a negative field length");
        }

        long expectedSize = (long) ENTRY_HEADER_SIZE + keySize + valueSize;

        if (expectedSize != data.length) {
            throw new CorruptEntryException("Entry for key '" + key + "' declares " + expectedSize
                    + " bytes but the stored record contains " + data.length);
        }

        byte[] keyBytes = new byte[keySize];
        byte[] valueBytes = new byte[valueSize];
        buffer.get(keyBytes);
        buffer.get(valueBytes);

        String storedKey = new String(keyBytes, StandardCharsets.UTF_8);
        if (!key.equals(storedKey)) {
            throw new CorruptEntryException("Key directory points to a record for a different key");
        }

        return Optional.of(new Entry(
                timestamp,
                storedKey,
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
    private byte[] serializeEntry(Entry entry) {
        // <timestamp><key-size><value-size><key><value>
        byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
        int totalSize = ENTRY_HEADER_SIZE + keyBytes.length + valueBytes.length;

        return ByteBuffer.allocate(totalSize)
                .putLong(entry.getTimestamp())
                .putInt(keyBytes.length)
                .putInt(valueBytes.length)
                .put(keyBytes)
                .put(valueBytes)
                .array();
    }

    @Override
    public void close() throws IOException {
        segmentsManager.close();
    }
}
