package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.segment.RecoveredEntry;
import org.example.adapter.outbound.bitcask.segment.Segment;
import org.example.adapter.outbound.bitcask.segment.SegmentRecoverer;
import org.example.adapter.outbound.bitcask.segment.SegmentsManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;


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

        recover();
    }

    // we get the segments from our SegmentManager, and we recover our KeyDir from it.
    // We don't make the SegmentManager to recover the KeyDir for us, as the SegmentManager don't know how our entries are stored.
    // TODO: For now, we didn't use/implement any hint files yet.
    private void recover() throws IOException {
        List<Segment> segments = this.segmentsManager.getSegments();
        for (Segment segment : segments)
            recoverSegment(segment);
    }

    private void recoverSegment(Segment segment) throws IOException {
        SegmentRecoverer recoverer = new SegmentRecoverer(segment);

        // the segments were ordered by the fileId
        // so whenever we get a value, we can update the keyDir without checking any old values
        while (recoverer.hasNext()) {
            RecoveredEntry recoveredEntry = recoverer.get();
            keyDir.put(recoveredEntry.key(), recoveredEntry.location());
        }
    }



    public synchronized void set(Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        String key = Objects.requireNonNull(entry.key(), "entry.key");
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
