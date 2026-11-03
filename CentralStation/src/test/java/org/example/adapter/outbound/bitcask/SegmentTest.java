
package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
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
    void appendsRecordsAndReturnsEachEndingOffset() throws Exception {

        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second record".getBytes(StandardCharsets.UTF_8);

        Path path = tempDir.resolve("append.data");

        try (Segment segment = new Segment("append", path)) {

            long firstEndOffset = segment.append(first);
            long secondEndOffset = segment.append(second);

            assertEquals(first.length, firstEndOffset);
            assertEquals(
                    first.length + second.length,
                    secondEndOffset
            );
            assertEquals(
                    first.length + second.length,
                    segment.size()
            );

            long firstStartOffset = firstEndOffset - first.length;
            long secondStartOffset = secondEndOffset - second.length;

            assertEquals(0L, firstStartOffset);
            assertEquals(first.length, secondStartOffset);
            assertArrayEquals(
                    first,
                    segment.read(firstStartOffset, first.length)
            );
            assertArrayEquals(
                    second,
                    segment.read(secondStartOffset, second.length)
            );
        }
    }

    @Test
    void supportsEmptyAppendAndZeroLengthRead() throws Exception {

        Path path = tempDir.resolve("empty.data");

        byte[] payload = bytes("existing data");

        try (Segment segment = new Segment("empty", path)) {

            long firstEndOffset = segment.append(payload);
            long emptyAppendEndOffset = segment.append(new byte[0]);

            assertEquals(firstEndOffset, emptyAppendEndOffset);
            assertEquals(payload.length, segment.size());
            assertArrayEquals(
                    new byte[0],
                    segment.read(firstEndOffset, 0)
            );
            assertArrayEquals(
                    new byte[0],
                    segment.read(0, 0)
            );
        }
    }

    @Test
    void sealedSegmentRejectsWritesButRemainsReadable() throws Exception {

        byte[] data = bytes("persisted");

        Path path = tempDir.resolve("sealed.data");

        try (Segment segment = new Segment("sealed", path)) {

            long endOffset = segment.append(data);
            long startOffset = endOffset - data.length;

            segment.seal();

            assertThrows(
                    IllegalStateException.class,
                    () -> segment.append(bytes("must not be appended"))
            );
            assertArrayEquals(
                    data,
                    segment.read(startOffset, data.length)
            );

            assertEquals(data.length, segment.size());
        }
    }

    @Test
    void rejectsInvalidReadArguments() throws Exception {

        Path path = tempDir.resolve("invalid-read.data");

        try (Segment segment = new Segment("invalid-read", path)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(-1, 0)
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(0, -1)
            );
            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(
                            0,
                            (long) Integer.MAX_VALUE + 1
                    )
            );
        }
    }

    @Test
    void throwsEofExceptionWhenReadExtendsBeyondEndOfFile()
            throws Exception {

        Path path = tempDir.resolve("eof.data");

        try (Segment segment = new Segment("eof", path)) {

            segment.append(new byte[]{10, 20, 30});

            assertThrows(
                    EOFException.class,
                    () -> segment.read(1, 3)
            );
        }
    }

    @Test
    void throwsEofExceptionWhenReadStartsBeyondEndOfFile()
            throws Exception {

        Path path = tempDir.resolve("beyond-eof.data");

        try (Segment segment = new Segment("beyond-eof", path)) {

            segment.append(new byte[]{10, 20, 30});

            assertThrows(
                    EOFException.class,
                    () -> segment.read(10, 1)
            );
        }
    }

    @Test
    void doesNotTruncateAnExistingFileWhenCreationFails()
            throws Exception {

        Path path = tempDir.resolve("existing.data");

        byte[] existingData = bytes("existing segment contents");

        Files.write(path, existingData);

        assertThrows(
                FileAlreadyExistsException.class,
                () -> new Segment("existing", path)
        );
        assertArrayEquals(
                existingData,
                Files.readAllBytes(path)
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
