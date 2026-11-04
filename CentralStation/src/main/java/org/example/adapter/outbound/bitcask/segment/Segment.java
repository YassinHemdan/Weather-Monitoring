package org.example.adapter.outbound.bitcask.segment;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Segment implements AutoCloseable{

    private final String fileId;
    private final FileChannel fileChannel;
    private SegmentState state;

    public Segment(String fileId, Path path, SegmentState state, boolean createNew) throws IOException {
        this.fileId = Objects.requireNonNull(fileId, "file_id");
        this.state = Objects.requireNonNull(state, "segment_state");
        this.fileChannel = getFileChannel(path, createNew);
    }

    private FileChannel getFileChannel(Path path, boolean isNew) throws IOException {
        List<OpenOption> options = new ArrayList<>();
        options.add(StandardOpenOption.READ);
        options.add(StandardOpenOption.WRITE);

        if (isNew)
            options.add(StandardOpenOption.CREATE_NEW);

        return FileChannel.open(path, options.toArray(new OpenOption[0]));
    }

    public byte[] read(long offset, long length) throws IOException {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
        if (length < 0 || length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Length must be between 0 and Integer.MAX_VALUE");
        }

        ByteBuffer buffer = ByteBuffer.allocate((int) length);
        long position = offset;

        while (buffer.hasRemaining()) {
            int bytesRead = fileChannel.read(buffer, position);
            if (bytesRead < 0) {
                throw new EOFException("Unexpected end of segment " + fileId
                        + " while reading at offset " + position);
            }
            if (bytesRead == 0) {
                Thread.onSpinWait();
                continue;
            }
            position += bytesRead;
        }

        return buffer.array();
    }

    public synchronized long append(byte[] data) throws IOException {
        Objects.requireNonNull(data, "data");
        if (state == SegmentState.SEALED) {
            throw new IllegalStateException("Cannot append to sealed segment " + fileId);
        }

        long startOffset = fileChannel.size();
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long position = startOffset;

        while (buffer.hasRemaining()) {
            int written = fileChannel.write(buffer, position);
            if (written == 0) {
                Thread.onSpinWait();
                continue;
            }
            position += written;
        }
        return position;
    }

    public String fileId() {
        return this.fileId;
    }

    public long size() throws IOException {
        return fileChannel.size();
    }

    // The file will be logically immutable, but the actual permissions of the file still read/write
    public synchronized void seal() throws IOException {
        state = SegmentState.SEALED;
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }
}
