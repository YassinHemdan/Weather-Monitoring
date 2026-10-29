package org.example.adapter.outbound.bitcask;

import java.io.IOException;

public final class SegmentNotFoundException extends IOException {
    public SegmentNotFoundException(String fileId) {
        super("Segment '" + fileId + "' is not available");
    }
}