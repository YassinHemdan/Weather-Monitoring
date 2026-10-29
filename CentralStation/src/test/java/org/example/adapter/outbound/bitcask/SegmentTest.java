package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAnEmptyFileAndReportsItsIdAndSize() throws Exception {
        Path path = tempDir.resolve("segment-1.data");

        try (Segment segment = new Segment("segment-1", path)) {
            assertTrue(Files.exists(path));
            assertEquals("segment-1", segment.fileId());
            assertEquals(0L, segment.size());
        }
    }

    @Test
    void appendsRecordsAndReturnsEachStartingOffset() throws Exception {
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second record".getBytes(StandardCharsets.UTF_8);
        Path path = tempDir.resolve("append.data");

        try (Segment segment = new Segment("append", path)) {
            long firstOffset = segment.append(first);
            long secondOffset = segment.append(second);

            assertEquals(0L, firstOffset);
            assertEquals(first.length, secondOffset);
            assertEquals(first.length + second.length, segment.size());
            assertArrayEquals(first, segment.read(firstOffset, first.length));
            assertArrayEquals(second, segment.read(secondOffset, second.length));
        }
    }

    @Test
    void supportsAnEmptyAppendAndZeroLengthRead() throws Exception {
        Path path = tempDir.resolve("empty.data");

        try (Segment segment = new Segment("empty", path)) {
            assertEquals(0L, segment.append(new byte[0]));
            assertArrayEquals(new byte[0], segment.read(0, 0));
            assertEquals(0L, segment.size());
        }
    }

    @Test
    void sealedSegmentRejectsWritesButRemainsReadable() throws Exception {
        byte[] data = "persisted".getBytes(StandardCharsets.UTF_8);
        Path path = tempDir.resolve("sealed.data");

        try (Segment segment = new Segment("sealed", path)) {
            long offset = segment.append(data);
            segment.seal();

            assertThrows(IllegalStateException.class,
                    () -> segment.append("must not be appended".getBytes(StandardCharsets.UTF_8)));
            assertArrayEquals(data, segment.read(offset, data.length));
            assertEquals(data.length, segment.size());
        }
    }

    @Test
    void rejectsInvalidReadArguments() throws Exception {
        Path path = tempDir.resolve("invalid-read.data");

        try (Segment segment = new Segment("invalid-read", path)) {
            assertThrows(IllegalArgumentException.class, () -> segment.read(-1, 0));
            assertThrows(IllegalArgumentException.class, () -> segment.read(0, -1));
            assertThrows(IllegalArgumentException.class,
                    () -> segment.read(0, (long) Integer.MAX_VALUE + 1));
        }
    }

    @Test
    void throwsEofExceptionWhenReadExtendsBeyondTheEndOfTheFile() throws Exception {
        Path path = tempDir.resolve("eof.data");

        try (Segment segment = new Segment("eof", path)) {
            segment.append(new byte[]{10, 20, 30});

            assertThrows(EOFException.class, () -> segment.read(1, 3));
        }
    }
}