package org.example.adapter.outbound.bitcask;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class Segment implements AutoCloseable{
    private enum State {
        ACTIVE,
        SEALED
    }

    private final String fileId;
    private final FileChannel fileChannel;
    private volatile State state = State.ACTIVE;

    public Segment(String fileId, Path path) throws IOException {
        this.fileId = Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(path, "path");

        this.fileChannel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE
        );
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
        if (state == State.SEALED) {
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
        return startOffset;
    }

    public String fileId() {
        return this.fileId;
    }
    public long size() throws IOException {
        return fileChannel.size();
    }
    public synchronized void seal() {
        state = State.SEALED;
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }
}
