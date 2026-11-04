package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.segment.Segment;
import org.example.adapter.outbound.bitcask.segment.SegmentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {

    @TempDir
    Path tempDir;

    @Test
    void appendWritesAtEndAndReturnsExclusiveEndOffset() throws Exception {
        Path file = tempDir.resolve("100.data");

        try (Segment segment = new Segment("100", file, SegmentState.ACTIVE, true)) {
            byte[] first = "first".getBytes(StandardCharsets.UTF_8);
            byte[] second = "-second".getBytes(StandardCharsets.UTF_8);

            assertEquals(first.length, segment.append(first));
            assertEquals(first.length + second.length, segment.append(second));
            assertEquals(first.length + second.length, segment.size());
            assertArrayEquals("first-second".getBytes(StandardCharsets.UTF_8),
                    segment.read(0, first.length + second.length));
        }
    }

    @Test
    void positionalReadsDoNotDependOnPreviousReadPosition() throws Exception {
        Path file = tempDir.resolve("101.data");

        try (Segment segment = new Segment("101", file, SegmentState.ACTIVE, true)) {
            segment.append("abcdefghij".getBytes(StandardCharsets.UTF_8));

            assertArrayEquals("efg".getBytes(StandardCharsets.UTF_8), segment.read(4, 3));
            assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), segment.read(0, 3));
            assertArrayEquals("hij".getBytes(StandardCharsets.UTF_8), segment.read(7, 3));
        }
    }

    @Test
    void readingPastEndOfFileThrowsEofException() throws Exception {
        Path file = tempDir.resolve("102.data");

        try (Segment segment = new Segment("102", file, SegmentState.ACTIVE, true)) {
            segment.append(new byte[]{1, 2, 3});

            assertThrows(EOFException.class, () -> segment.read(1, 3));
        }
    }

    @Test
    void sealedSegmentRejectsFurtherAppends() throws Exception {
        Path file = tempDir.resolve("103.data");

        try (Segment segment = new Segment("103", file, SegmentState.ACTIVE, true)) {
            segment.append(new byte[]{1, 2, 3});
            segment.seal();

            assertThrows(IllegalStateException.class,
                    () -> segment.append(new byte[]{4}));
            assertArrayEquals(new byte[]{1, 2, 3}, segment.read(0, 3));
        }
    }

    @Test
    void rejectsNegativeOffsetsAndLengths() throws Exception {
        Path file = tempDir.resolve("104.data");

        try (Segment segment = new Segment("104", file, SegmentState.ACTIVE, true)) {
            assertThrows(IllegalArgumentException.class, () -> segment.read(-1, 1));
            assertThrows(IllegalArgumentException.class, () -> segment.read(0, -1));
        }
    }
}