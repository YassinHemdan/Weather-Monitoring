package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.ValueLocation;
import org.example.adapter.outbound.bitcask.segment.RecoveredEntry;
import org.example.adapter.outbound.bitcask.segment.Segment;
import org.example.adapter.outbound.bitcask.segment.SegmentRecoverer;
import org.example.adapter.outbound.bitcask.segment.SegmentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SegmentRecovererTest {

    @TempDir
    Path tempDir;

    @Test
    void recoversKeyAndValueLocationFromACompleteRecord() throws Exception {
        String key = "محطة-42";
        String value = "درجة الحرارة 23°C";
        byte[] record = encodeRecord(123L, key, value);

        try (Segment segment = new Segment("200", tempDir.resolve("200.data"),
                SegmentState.ACTIVE, true)) {
            segment.append(record);

            SegmentRecoverer recoverer = new SegmentRecoverer(segment);
            assertTrue(recoverer.hasNext());

            RecoveredEntry recovered = recoverer.get();
            ValueLocation location = recovered.location();

            assertEquals(key, recovered.key());
            assertEquals("200", location.fileId());
            assertEquals(123L, location.timestamp());
            assertEquals(value.getBytes(StandardCharsets.UTF_8).length, location.valueSize());
            assertEquals(Long.BYTES + 2 * Integer.BYTES
                            + key.getBytes(StandardCharsets.UTF_8).length,
                    location.valuePos());
            assertFalse(recoverer.hasNext());
        }
    }

    @Test
    void recoversMultipleRecordsInAppendOrder() throws Exception {
        byte[] first = encodeRecord(1L, "key-1", "value-1");
        byte[] second = encodeRecord(2L, "key-2", "value-2");

        try (Segment segment = new Segment("201", tempDir.resolve("201.data"),
                SegmentState.ACTIVE, true)) {
            segment.append(first);
            segment.append(second);

            SegmentRecoverer recoverer = new SegmentRecoverer(segment);

            assertEquals("key-1", recoverer.get().key());
            assertEquals("key-2", recoverer.get().key());
            assertFalse(recoverer.hasNext());
        }
    }

    @Test
    void emptySegmentHasNoRecords() throws Exception {
        try (Segment segment = new Segment("202", tempDir.resolve("202.data"),
                SegmentState.ACTIVE, true)) {
            SegmentRecoverer recoverer = new SegmentRecoverer(segment);
            assertFalse(recoverer.hasNext());
        }
    }

    @Test
    void rejectsARecordWhoseDeclaredValueExtendsPastEndOfSegment() throws Exception {
        byte[] key = "k".getBytes(StandardCharsets.UTF_8);
        byte[] partialValue = "xy".getBytes(StandardCharsets.UTF_8);

        // Header declares a 5-byte value, but only 2 value bytes are present.
        byte[] incompleteRecord = ByteBuffer.allocate(
                        Long.BYTES + 2 * Integer.BYTES + key.length + partialValue.length)
                .putLong(7L)
                .putInt(key.length)
                .putInt(5)
                .put(key)
                .put(partialValue)
                .array();

        try (Segment segment = new Segment("203", tempDir.resolve("203.data"),
                SegmentState.ACTIVE, true)) {
            segment.append(incompleteRecord);
            SegmentRecoverer recoverer = new SegmentRecoverer(segment);

            // Expected once SegmentRecoverer validates keySize + valueSize against remaining bytes.
            assertThrows(IOException.class, recoverer::get);
        }
    }

    private static byte[] encodeRecord(long timestamp, String key, String value) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

        return ByteBuffer.allocate(Long.BYTES + 2 * Integer.BYTES
                        + keyBytes.length + valueBytes.length)
                .putLong(timestamp)
                .putInt(keyBytes.length)
                .putInt(valueBytes.length)
                .put(keyBytes)
                .put(valueBytes)
                .array();
    }
}