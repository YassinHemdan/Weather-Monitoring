package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.record.EncodedRecord;
import org.example.adapter.outbound.bitcask.record.RecordCodec;
import org.example.adapter.outbound.bitcask.segment.AppendResult;
import org.example.adapter.outbound.bitcask.segment.SegmentsManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class Database implements AutoCloseable{
    private final SegmentsManager segmentsManager;
    private final RecordCodec recordCodec = new RecordCodec();
    private final Map<String, ValueLocation> keyDir = new ConcurrentHashMap<>();

    public Database(String dbPath) throws IOException {
        this(dbPath, SegmentsManager.DEFAULT_MAX_FILE_SIZE);
    }

    public Database(String dbPath, long maxSegmentSize) throws IOException {
        Path dbDir = Paths.get(Objects.requireNonNull(dbPath, "dbPath"));
        this.segmentsManager = new SegmentsManager(dbDir, maxSegmentSize);

        recover();
    }

    private void recover() throws IOException {
        KeyDirRecovery recovery = new KeyDirRecovery(recordCodec, segmentsManager);
        keyDir.putAll(recovery.recover());
    }

    // the actual offset of the value computed as follows:
    // the valueOffset in the encodedRecord is the starting location of the value in the byte array (not in the file)
    // the startOffset in the AppendResult is the starting location of the whole record in the file
    // so the actual position of the value will be valueOffset + startOffset
    public synchronized void set(Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.key(), "entry.key");
        Objects.requireNonNull(entry.value(), "entry.value");

        EncodedRecord encodedRecord = recordCodec.encode(entry);

        AppendResult result = segmentsManager.append(encodedRecord.data());

        long valuePos = encodedRecord.valueOffset() + result.startOffset();

        keyDir.put(
                entry.key(),
                new ValueLocation(
                        result.segmentId(),
                        encodedRecord.valueLength(),
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
                recordCodec.decodeValue(valueBytes)
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

    @Override
    public void close() throws IOException {
        segmentsManager.close();
    }
}
