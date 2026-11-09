package org.example.adapter.outbound.bitcask.segment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SegmentTest {

    @TempDir
    Path tempDir;

    @Test
    void appendShouldWriteDataAndReturnStartingOffset() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            long firstOffset = segment.append(new byte[]{1, 2, 3});
            long secondOffset = segment.append(new byte[]{4, 5});

            assertEquals(0, firstOffset);
            assertEquals(3, secondOffset);
            assertEquals(5, segment.size());
        }
    }

    @Test
    void readShouldReturnCorrectBytes() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            segment.append(new byte[]{1, 2, 3, 4, 5});

            byte[] result = segment.read(1, 3);

            assertArrayEquals(
                    new byte[]{2, 3, 4},
                    result
            );
        }
    }

    @Test
    void readShouldRejectNegativeOffset() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(-1, 1)
            );
        }
    }

    @Test
    void readShouldRejectInvalidLength() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(0, -1)
            );

            assertThrows(
                    IllegalArgumentException.class,
                    () -> segment.read(0, (long) Integer.MAX_VALUE + 1
                    )
            );
        }
    }

    @Test
    void readShouldThrowWhenRequestedBytesDoNotExist() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            segment.append(new byte[]{1, 2});

            assertThrows(
                    EOFException.class,
                    () -> segment.read(0, 3)
            );
        }
    }

    @Test
    void sealShouldPreventFurtherAppends() throws Exception {
        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            segment.append(new byte[]{1, 2});

            segment.seal();

            assertEquals(SegmentState.SEALED, segment.getState());

            assertThrows(
                    IllegalStateException.class,
                    () -> segment.append(new byte[]{3})
            );

            assertEquals(2, segment.size());
        }
    }

    @Test
    void concurrentAppendsShouldNotOverlap() throws Exception {
        int threadCount = 4;
        int writesPerThread = 100;
        int totalWrites = threadCount * writesPerThread;

        try (Segment segment = new Segment(
                "1",
                tempDir.resolve("1.data"),
                SegmentState.ACTIVE,
                true
        )) {
            ExecutorService executor =
                    Executors.newFixedThreadPool(threadCount);

            try {
                List<Callable<Long>> tasks = new ArrayList<>();

                for (int i = 0; i < totalWrites; i++) {
                    int value = i;

                    tasks.add(() -> segment.append(
                            ByteBuffer.allocate(Integer.BYTES)
                                    .putInt(value)
                                    .array()
                    ));
                }

                List<Future<Long>> futures = executor.invokeAll(tasks);

                Set<Long> offsets = new HashSet<>();

                for (Future<Long> future : futures) {
                    offsets.add(future.get());
                }

                assertEquals(totalWrites, offsets.size());
                assertEquals(totalWrites * Integer.BYTES, segment.size());

                Set<Integer> writtenValues = new HashSet<>();

                for (long offset : offsets) {
                    byte[] data = segment.read(offset, Integer.BYTES);

                    writtenValues.add(
                            ByteBuffer.wrap(data).getInt()
                    );
                }

                assertEquals(totalWrites, writtenValues.size());

            } finally {
                executor.shutdownNow();
            }
        }
    }
}