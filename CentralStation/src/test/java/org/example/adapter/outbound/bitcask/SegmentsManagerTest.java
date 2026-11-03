
package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentsManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDatabaseDirectoryAndInitialSegment() throws Exception {

        Path dbDir = tempDir.resolve("new-database");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 32)) {

            byte[] payload = bytes("first record");

            WriteResult result = append(manager, payload);

            assertTrue(Files.isDirectory(dbDir));
            assertTrue(Files.exists(
                    dbDir.resolve(result.fileId() + ".data")));

            assertEquals(0L, result.startOffset());
            assertEquals((long) payload.length, result.endOffset());

            assertArrayEquals(payload, read(manager, result));
        }
    }

    @Test
    void appendsToCurrentSegmentUntilTheNextRecordWillNotFit()
            throws Exception {

        Path dbDir = tempDir.resolve("rotation-boundary");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 8)) {

            byte[] first = new byte[]{1, 2, 3, 4};
            byte[] second = new byte[]{5, 6, 7, 8};
            byte[] third = new byte[]{9};

            WriteResult firstResult = append(manager, first);
            WriteResult secondResult = append(manager, second);
            WriteResult thirdResult = append(manager, third);

            assertEquals(firstResult.fileId(), secondResult.fileId());

            assertEquals(0L, firstResult.startOffset());
            assertEquals(4L, firstResult.endOffset());

            assertEquals(4L, secondResult.startOffset());
            assertEquals(8L, secondResult.endOffset());

            assertNotEquals(
                    secondResult.fileId(),
                    thirdResult.fileId());

            assertEquals(0L, thirdResult.startOffset());
            assertEquals(1L, thirdResult.endOffset());

            assertArrayEquals(first, read(manager, firstResult));
            assertArrayEquals(second, read(manager, secondResult));
            assertArrayEquals(third, read(manager, thirdResult));
        }
    }

    @Test
    void keepsOlderSegmentsReadableAfterSeveralRotations()
            throws Exception {

        Path dbDir = tempDir.resolve("multiple-rotations");

        List<byte[]> payloads = List.of(
                bytes("aaaa"),
                bytes("bbbb"),
                bytes("cccc"),
                bytes("dddd"),
                bytes("eeee"),
                bytes("ffff")
        );

        try (SegmentsManager manager = new SegmentsManager(dbDir, 4)) {

            List<WriteResult> locations = new ArrayList<>();

            for (byte[] payload : payloads) {
                locations.add(append(manager, payload));
            }

            long distinctSegmentCount = locations.stream()
                    .map(WriteResult::fileId)
                    .distinct()
                    .count();

            assertEquals(payloads.size(), distinctSegmentCount);

            try (var paths = Files.list(dbDir)) {

                assertEquals(
                        payloads.size(),
                        paths.filter(path ->
                                path.toString().endsWith(".data")).count()
                );
            }

            for (int i = 0; i < payloads.size(); i++) {

                assertArrayEquals(
                        payloads.get(i),
                        read(manager, locations.get(i))
                );
            }
        }
    }

    @Test
    void rejectsOversizedRecordWithoutDisablingCurrentActiveSegment()
            throws Exception {

        Path dbDir = tempDir.resolve("oversized-record");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 4)) {

            WriteResult first =
                    append(manager, new byte[]{1, 2, 3});

            assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.write(new byte[]{1, 2, 3, 4, 5})
            );

            WriteResult second =
                    append(manager, new byte[]{4});

            assertEquals(first.fileId(), second.fileId());

            assertEquals(0L, first.startOffset());
            assertEquals(3L, second.startOffset());

            assertArrayEquals(
                    new byte[]{1, 2, 3},
                    read(manager, first)
            );

            assertArrayEquals(
                    new byte[]{4},
                    read(manager, second)
            );
        }
    }

    @Test
    void rejectsNonPositiveMaximumSegmentSize() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentsManager(
                        tempDir.resolve("zero-size"), 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentsManager(
                        tempDir.resolve("negative-size"), -1)
        );
    }

    @Test
    void reportsUnknownSegmentIdAsSegmentNotFound()
            throws Exception {

        try (SegmentsManager manager =
                     new SegmentsManager(
                             tempDir.resolve("unknown"), 32)) {

            assertThrows(
                    SegmentNotFoundException.class,
                    () -> manager.read(0, "does-not-exist", 1)
            );
        }
    }

    @Test
    void rejectsRecordLargerThanMaximumBeforeWritingAnything()
            throws Exception {

        Path dbDir = tempDir.resolve("too-large-first-record");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 3)) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.write(new byte[]{1, 2, 3, 4})
            );

            WriteResult firstValid =
                    append(manager, new byte[]{7, 8, 9});

            assertEquals(0L, firstValid.startOffset());
            assertEquals(3L, firstValid.endOffset());

            assertArrayEquals(
                    new byte[]{7, 8, 9},
                    read(manager, firstValid)
            );
        }
    }

    @Test
    void concurrentWritersProduceReadableNonOverlappingRecordsAcrossRotations()
            throws Exception {

        Path dbDir = tempDir.resolve("concurrent-writes");

        int writerCount = 4;
        int recordsPerWriter = 100;
        int recordSize = Integer.BYTES * 2;

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(writerCount);

        Map<String, StoredRecord> writtenRecords =
                new ConcurrentHashMap<>();

        try (SegmentsManager manager = new SegmentsManager(dbDir, 64)) {

            List<Future<?>> futures = new ArrayList<>();

            for (int writer = 0; writer < writerCount; writer++) {

                final int writerId = writer;

                futures.add(executor.submit(() -> {

                    start.await();

                    for (int record = 0;
                         record < recordsPerWriter;
                         record++) {

                        String key = writerId + ":" + record;

                        byte[] payload = ByteBuffer.allocate(recordSize)
                                .putInt(writerId)
                                .putInt(record)
                                .array();

                        WriteResult location = append(manager, payload);

                        StoredRecord storedRecord =
                                new StoredRecord(payload, location);

                        if (writtenRecords.putIfAbsent(
                                key, storedRecord) != null) {

                            throw new AssertionError(
                                    "Duplicate test key: " + key);
                        }
                    }

                    return null;
                }));
            }
            start.countDown();

            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertEquals(
                    writerCount * recordsPerWriter,
                    writtenRecords.size()
            );
            for (StoredRecord record : writtenRecords.values()) {

                assertArrayEquals(
                        record.payload(),
                        read(manager, record.location())
                );
            }
            assertTrue(
                    writtenRecords.values().stream()
                            .map(record ->
                                    record.location().fileId())
                            .distinct()
                            .count() > 1,
                    "The small segment size should force rotations"
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closeClosesManagedSegments() throws Exception {

        SegmentsManager manager =
                new SegmentsManager(tempDir.resolve("close"), 32);

        WriteResult location =
                append(manager, bytes("close me"));

        manager.close();

        assertThrows(
                IOException.class,
                () -> read(manager, location)
        );
    }

    private static WriteResult append(
            SegmentsManager manager,
            byte[] payload
    ) throws IOException {

        synchronized (manager) {

            long endOffset = manager.write(payload);

            String fileId = manager.activeFileId();

            long startOffset = endOffset - payload.length;

            return new WriteResult(
                    fileId,
                    startOffset,
                    endOffset,
                    payload.length
            );
        }
    }

    private static byte[] read(
            SegmentsManager manager,
            WriteResult location
    ) throws IOException {

        return manager.read(
                location.startOffset(),
                location.fileId(),
                location.length()
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record WriteResult(
            String fileId,
            long startOffset,
            long endOffset,
            int length
    ) {
    }

    private record StoredRecord(
            byte[] payload,
            WriteResult location
    ) {
    }
}
