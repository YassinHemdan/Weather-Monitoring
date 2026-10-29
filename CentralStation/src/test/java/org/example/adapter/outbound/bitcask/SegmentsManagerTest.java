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
            EntryLocation location = manager.write(payload);

            assertTrue(Files.isDirectory(dbDir));
            assertTrue(Files.exists(dbDir.resolve(location.fileId() + ".data")));
            assertEquals(0L, location.offset());
            assertEquals(payload.length, location.entrySize());
            assertArrayEquals(payload,
                    manager.read(location.offset(), location.fileId(), location.entrySize()));
        }
    }

    @Test
    void appendsToCurrentSegmentUntilTheNextRecordWillNotFit() throws Exception {
        Path dbDir = tempDir.resolve("rotation-boundary");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 8)) {
            byte[] first = new byte[]{1, 2, 3, 4};
            byte[] second = new byte[]{5, 6, 7, 8};
            byte[] third = new byte[]{9};

            EntryLocation firstLocation = manager.write(first);
            EntryLocation secondLocation = manager.write(second);
            EntryLocation thirdLocation = manager.write(third);

            assertEquals(firstLocation.fileId(), secondLocation.fileId());
            assertEquals(0L, firstLocation.offset());
            assertEquals(first.length, secondLocation.offset());
            assertNotEquals(secondLocation.fileId(), thirdLocation.fileId());
            assertEquals(0L, thirdLocation.offset());

            assertArrayEquals(first, read(manager, firstLocation));
            assertArrayEquals(second, read(manager, secondLocation));
            assertArrayEquals(third, read(manager, thirdLocation));
        }
    }

    @Test
    void keepsOlderSegmentsReadableAfterSeveralRotations() throws Exception {
        Path dbDir = tempDir.resolve("multiple-rotations");
        List<byte[]> payloads = List.of(
                bytes("aaaa"), bytes("bbbb"), bytes("cccc"),
                bytes("dddd"), bytes("eeee"), bytes("ffff")
        );

        try (SegmentsManager manager = new SegmentsManager(dbDir, 4)) {
            List<EntryLocation> locations = new ArrayList<>();
            for (byte[] payload : payloads) {
                locations.add(manager.write(payload));
            }

            long distinctSegmentCount = locations.stream()
                    .map(EntryLocation::fileId)
                    .distinct()
                    .count();
            assertEquals(payloads.size(), distinctSegmentCount);
            try (var paths = Files.list(dbDir)) {
                assertEquals(payloads.size(),
                        paths.filter(path -> path.toString().endsWith(".data")).count());
            }

            for (int i = 0; i < payloads.size(); i++) {
                assertArrayEquals(payloads.get(i), read(manager, locations.get(i)));
            }
        }
    }

    @Test
    void rejectsOversizedRecordWithoutDisablingCurrentActiveSegment() throws Exception {
        Path dbDir = tempDir.resolve("oversized-record");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 4)) {
            EntryLocation first = manager.write(new byte[]{1, 2, 3});

            assertThrows(IllegalArgumentException.class,
                    () -> manager.write(new byte[]{1, 2, 3, 4, 5}));

            EntryLocation second = manager.write(new byte[]{4});
            assertEquals(first.fileId(), second.fileId());
            assertEquals(3L, second.offset());
            assertArrayEquals(new byte[]{1, 2, 3}, read(manager, first));
            assertArrayEquals(new byte[]{4}, read(manager, second));
        }
    }

    @Test
    void rejectsNonPositiveMaximumSegmentSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new SegmentsManager(tempDir.resolve("zero-size"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SegmentsManager(tempDir.resolve("negative-size"), -1));
    }

    @Test
    void reportsUnknownSegmentIdAsSegmentNotFound() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir.resolve("unknown"), 32)) {
            assertThrows(SegmentNotFoundException.class,
                    () -> manager.read(0, "does-not-exist", 1));
        }
    }

    @Test
    void rejectsRecordLargerThanMaximumBeforeWritingAnything() throws Exception {
        Path dbDir = tempDir.resolve("too-large-first-record");

        try (SegmentsManager manager = new SegmentsManager(dbDir, 3)) {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.write(new byte[]{1, 2, 3, 4}));

            EntryLocation firstValid = manager.write(new byte[]{7, 8, 9});
            assertEquals(0L, firstValid.offset());
            assertArrayEquals(new byte[]{7, 8, 9}, read(manager, firstValid));
        }
    }

    @Test
    void concurrentWritersProduceReadableNonOverlappingRecordsAcrossRotations() throws Exception {
        Path dbDir = tempDir.resolve("concurrent-writes");
        int writerCount = 4;
        int recordsPerWriter = 100;
        int recordSize = Integer.BYTES * 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        Map<String, StoredRecord> writtenRecords = new ConcurrentHashMap<>();

        try (SegmentsManager manager = new SegmentsManager(dbDir, 64)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int writer = 0; writer < writerCount; writer++) {
                final int writerId = writer;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int record = 0; record < recordsPerWriter; record++) {
                        String key = writerId + ":" + record;
                        byte[] payload = ByteBuffer.allocate(recordSize)
                                .putInt(writerId)
                                .putInt(record)
                                .array();
                        EntryLocation location = manager.write(payload);
                        if (writtenRecords.putIfAbsent(key, new StoredRecord(payload, location)) != null) {
                            throw new AssertionError("Duplicate test key: " + key);
                        }
                    }
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }

            assertEquals(writerCount * recordsPerWriter, writtenRecords.size());
            for (StoredRecord record : writtenRecords.values()) {
                assertArrayEquals(record.payload(), read(manager, record.location()));
            }
            assertTrue(writtenRecords.values().stream()
                    .map(record -> record.location().fileId())
                    .distinct()
                    .count() > 1, "The small segment size should force rotations");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closeClosesManagedSegments() throws Exception {
        SegmentsManager manager = new SegmentsManager(tempDir.resolve("close"), 32);
        EntryLocation location = manager.write(bytes("close me"));
        manager.close();

        assertThrows(IOException.class,
                () -> manager.read(location.offset(), location.fileId(), location.entrySize()));
    }

    private static byte[] read(SegmentsManager manager, EntryLocation location) throws IOException {
        return manager.read(location.offset(), location.fileId(), location.entrySize());
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private record StoredRecord(byte[] payload, EntryLocation location) {
    }
}
