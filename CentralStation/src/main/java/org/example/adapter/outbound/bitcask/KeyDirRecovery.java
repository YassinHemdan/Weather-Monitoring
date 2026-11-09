package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.record.CorruptRecordException;
import org.example.adapter.outbound.bitcask.record.RecordCodec;
import org.example.adapter.outbound.bitcask.record.RecordMetadata;
import org.example.adapter.outbound.bitcask.segment.SegmentInfo;
import org.example.adapter.outbound.bitcask.segment.SegmentsManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class KeyDirRecovery {
    private final RecordCodec recordCodec;
    private final SegmentsManager segmentsManager;

    public KeyDirRecovery(RecordCodec recordCodec, SegmentsManager segmentsManager) {
        this.recordCodec = Objects.requireNonNull(recordCodec, "recordCodec");
        this.segmentsManager = Objects.requireNonNull(segmentsManager, "segmentsManager");
    }

    public Map<String, ValueLocation> recover() throws IOException {
        Map<String, ValueLocation> recoveredIndex = new HashMap<>();
        List<SegmentInfo> segmentInfoList = segmentsManager.getSegments();

        for (SegmentInfo segmentInfo : segmentInfoList) {
            recoverSegment(segmentInfo, recoveredIndex);
        }

        return recoveredIndex;
    }

    // recover each segment and its valueLocation to the hashmap
    // remember that the segments are sorted by their fileId
    private void recoverSegment(
            SegmentInfo segmentInfo,
            Map<String, ValueLocation> recoveredIndex
    ) throws IOException {

        String segmentId = segmentInfo.id();
        long segmentSize = segmentInfo.size();

        long offset = 0;

        int headerSize = recordCodec.headerSize();

        while (offset < segmentInfo.size()) {
            long remaining = segmentSize - offset;

            if (headerSize > remaining) {
                throw corruption(segmentId, offset, "Incomplete record header");
            }

            byte[] headerBytes = segmentsManager.read(offset, segmentInfo.id(), headerSize);
            int expectedRecordLength;
            try {
                expectedRecordLength = recordCodec.decodeHeader(headerBytes);
            } catch (CorruptRecordException e) {
                throw corruption(segmentId, offset, "Invalid record header", e);
            }

            if (expectedRecordLength > remaining) {
                throw corruption(
                        segmentId,
                        offset,
                        "Incomplete record. Expected "
                                    + expectedRecordLength
                                    + " but only "
                                    + remaining
                                    + " bytes remain"
                );
            }

            byte[] recordBytes = segmentsManager.read(offset, segmentId, expectedRecordLength);

            RecordMetadata metadata;
            try {
                metadata = recordCodec.decodeMetadata(recordBytes);
            } catch (CorruptRecordException e) {
                throw corruption(segmentId, offset, "Invalid record", e);
            }

            final long valueActualPosition;
            try {
                valueActualPosition = Math.addExact(offset, metadata.valueOffset());
            } catch (ArithmeticException e) {
                throw corruption(segmentId, offset, "Value position overflow", e);
            }

            ValueLocation location = new ValueLocation(
                    segmentId,
                    metadata.valueLength(),
                    valueActualPosition,
                    metadata.timestamp()
            );

            recoveredIndex.put(metadata.key(), location);
            offset += metadata.recordLength();
        }
    }

    private CorruptRecordException corruption(
            String segmentId,
            long offset,
            String message
    ) {
        return new CorruptRecordException(
                message + " in segment: "
                        + segmentId
                        + " at offset: "
                        + offset
        );
    }
    private CorruptRecordException corruption(
            String segmentId,
            long offset,
            String message,
            Throwable cause
    ) {
        return new CorruptRecordException(
                message + " in segment: "
                        + segmentId
                        + " at offset: "
                        + offset,
                cause
        );
    }
}
