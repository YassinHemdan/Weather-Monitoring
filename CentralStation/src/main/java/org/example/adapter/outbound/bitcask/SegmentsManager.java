package org.example.adapter.outbound.bitcask;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SegmentsManager implements AutoCloseable{
    public static final long DEFAULT_MAX_FILE_SIZE = 1_000_000_000L;

    private volatile Segment activeSegment;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    private final Path dbDir;

    private final long maxFileSize;
    private long lastId;

    // TODO: if there exist files, we should read them and detect
    // TODO:   where is the active segment and where are the sealed/read-only ones
    // TODO: compaction and merge.

    public SegmentsManager(Path dbDir) throws IOException {
        this(dbDir, DEFAULT_MAX_FILE_SIZE);
    }


    public SegmentsManager(Path dbDir, long maxFileSize) throws IOException {
        this.dbDir = Objects.requireNonNull(dbDir, "dbDir");
        if (maxFileSize <= 0) {
            throw new IllegalArgumentException("maxFileSize must be positive");
        }
        this.maxFileSize = maxFileSize;
        Files.createDirectories(dbDir);
        this.lastId = System.currentTimeMillis();
        this.activeSegment = createSegment();
    }

    public byte[] read(long offset, String fileId, long length) throws IOException {
        Objects.requireNonNull(fileId, "fileId");
        Segment segment = segments.get(fileId);
        if (segment == null) {
            throw new SegmentNotFoundException(fileId);
        }
        return segment.read(offset, length);
    }

    public synchronized EntryLocation write(byte[] data) throws IOException{
        Objects.requireNonNull(data, "data");
        if (data.length > maxFileSize) {
            throw new IllegalArgumentException("Record size " + data.length
                    + " exceeds maximum segment size " + maxFileSize);
        }

        Segment segment = activeSegment;
        long currentSize = segment.size();
        if (data.length > maxFileSize - currentSize) {
            newActiveSegment();
            segment = activeSegment;
        }

        long offset = segment.append(data);
        return new EntryLocation(segment.fileId(), offset, data.length);
    }

    private void newActiveSegment() throws IOException {
        Segment newActiveSegment = createSegment();
        Segment previousSegment = activeSegment;

        previousSegment.seal();
        activeSegment = newActiveSegment;
    }

    private Segment createSegment() throws IOException {
        while(true) {
            String id = nextId();
            Path segmentPath = dbDir.resolve(id + ".data");
            try {
                Segment segment = new Segment(id, segmentPath);
                segments.put(id, segment);
                return segment;
            } catch (FileAlreadyExistsException e) {
                // The idea is we will keep the while loop to get a unique id
                //      and try to create our segment
            }
        }
    }
    private synchronized String nextId() {
        lastId = Math.max(System.currentTimeMillis(), lastId + 1);
        return Long.toString(lastId);
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (Segment segment : segments.values()) {
            try {
                segment.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
