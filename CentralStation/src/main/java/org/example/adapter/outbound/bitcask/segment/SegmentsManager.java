package org.example.adapter.outbound.bitcask.segment;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SegmentsManager implements AutoCloseable{
    public static final long DEFAULT_MAX_FILE_SIZE = 10_000L;

    private volatile Segment activeSegment;
    private final Map<String, Segment> segments = new ConcurrentHashMap<>();

    private final Path dbDir;

    private final long maxFileSize;
    private long lastId;

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

        if (load(dbDir))
            return;

        this.lastId = System.currentTimeMillis();
        String segmentId = nextId();
        Segment segment = new Segment(
                segmentId,
                dbDir.resolve(segmentId + ".data"),
                SegmentState.ACTIVE,
                true
        );

        this.activeSegment = segment;
        this.segments.put(segmentId, segment);
    }

    private boolean load(Path dir) throws IOException {
        /*
        * We should load all segments in db dir.
        * we get every <filename>.data and sort by filename. The larger one is our active segment
        *   since that we generate the filename using Long  timestamps
        */
        List<String> fileIds;
        try(Stream<Path> stream = Files.list(dir)) {
            fileIds = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".data"))
                    .map(name -> {
                        int dotIdx = name.lastIndexOf('.');
                        return dotIdx > 0
                                ? name.substring(0, dotIdx)
                                : name;
                    })
                    .filter(name -> {
                        try {
                            Long.parseLong(name);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparingLong(Long::parseLong))
                    .toList();
        }

        if (fileIds.isEmpty())
            return false; // we have no data, now we should create a new active segment

        for(int i=0 ; i<fileIds.size() - 1; ++i) {
            openSegment(fileIds.get(i), SegmentState.SEALED);
        }

        String id = fileIds.getLast();

        this.activeSegment = openSegment(id, SegmentState.ACTIVE);
        this.lastId = Long.parseLong(id);

        return true;
    }

    public byte[] read(long offset, String fileId, long length) throws IOException {
        Objects.requireNonNull(fileId, "fileId");
        Segment segment = segments.get(fileId);
        if (segment == null) {
            throw new SegmentNotFoundException(fileId);
        }
        return segment.read(offset, length);
    }

    public synchronized long write(byte[] data) throws IOException{
        Objects.requireNonNull(data, "data");
        if (data.length > maxFileSize) {
            throw new IllegalArgumentException("Record size " + data.length
                    + " exceeds maximum segment size " + maxFileSize);
        }

        Segment segment = activeSegment;
        long currentSize = segment.size();
        if (data.length > maxFileSize - currentSize) {
            rotate();
            segment = activeSegment; // new active segment created
        }

        return segment.append(data);
    }


    private void rotate() throws IOException{
        while(true) {
            String id = nextId();
            Path segmentPath = dbDir.resolve(id + ".data");

            try {
                Segment newActiveSegment = new Segment(id, segmentPath, SegmentState.ACTIVE, true);
                segments.put(id, newActiveSegment);

                Segment previousSegment = activeSegment;

                activeSegment = newActiveSegment;
                previousSegment.seal();

                return;
            } catch (FileAlreadyExistsException ignored) {
                // The idea is we will keep the while loop to get a unique id
                //      and try to create our segment
            }
        }
    }

    // Opens an existed  segment, registers it in our manager, then returns it
    private Segment openSegment(String id, SegmentState state) throws IOException {
        Path segmentPath = dbDir.resolve(id + ".data");
        Segment segment = new Segment(id, segmentPath, state, false);

        this.segments.put(id, segment);

        return segment;
    }

    private synchronized String nextId() {
        lastId = Math.max(System.currentTimeMillis(), lastId + 1);
        return Long.toString(lastId);
    }

    public String activeFileId() {
        return this.activeSegment.fileId();
    }

    public List<Segment> getSegments() {
        return segments.values()
                .stream()
                .sorted(Comparator.comparingLong(
                        segment -> Long.parseLong(segment.fileId())
                ))
                .toList();
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
