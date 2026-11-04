package org.example.adapter.outbound.bitcask.segment;

import org.example.adapter.outbound.bitcask.ValueLocation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class SegmentRecoverer {
    private final Segment segment;
    private long offset;
    public SegmentRecoverer(Segment segment) {
        Objects.requireNonNull(segment);
        this.segment = segment;
        this.offset = 0;
    }

    public boolean hasNext() throws IOException {
        return offset < segment.size();
    }

    public RecoveredEntry get() throws IOException {
        // We know that entries are stored as <timestamp><key_size><value_size><key><value>
        String fileId = segment.fileId();

        long timestamp = ByteBuffer.wrap(segment.read(offset, Long.BYTES)).getLong();
        offset += Long.BYTES;

        int keySize = ByteBuffer.wrap(segment.read(offset, Integer.BYTES)).getInt();
        offset += Integer.BYTES;

        int valueSize = ByteBuffer.wrap(segment.read(offset, Integer.BYTES)).getInt();
        offset += Integer.BYTES;


        long remaining = segment.size() - offset;
        if (keySize < 0 || valueSize < 0) {
            throw new CorruptEntryException("Negative key or value size");
        }
        if ((long) keySize + valueSize > remaining) {
            throw new CorruptEntryException(
                    "Incomplete record in segment " + fileId
            );
        }


        String key = new String(segment.read(offset, keySize), StandardCharsets.UTF_8);
        offset += keySize;

        // at this point, we have the valuePosition
        ValueLocation location = new ValueLocation(fileId, valueSize, offset, timestamp);

        // we don't care about the value itself, we just move the offset
        offset += valueSize;

        return new RecoveredEntry(key, location);
    }
}
