package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.record.CorruptRecordException;
import org.example.adapter.outbound.bitcask.record.RecordCodec;
import org.example.adapter.outbound.bitcask.segment.SegmentsManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KeyDirRecoveryTest {

    @TempDir
    Path tempDir;

    private final RecordCodec codec = new RecordCodec();

    @Test
    void recoverShouldRebuildKeyDir() throws Exception {
        try (SegmentsManager manager =
                     new SegmentsManager(tempDir, 1024)) {

            manager.append(
                    codec.encode(
                            new Entry(100, "station-1", "25.5")
                    ).data()
            );

            manager.append(
                    codec.encode(
                            new Entry(200, "station-2", "30.5")
                    ).data()
            );

            Map<String, ValueLocation> recovered =
                    new KeyDirRecovery(codec, manager).recover();

            assertEquals(2, recovered.size());

            ValueLocation first = recovered.get("station-1");
            ValueLocation second = recovered.get("station-2");

            assertNotNull(first);
            assertNotNull(second);

            assertEquals(100, first.timestamp());
            assertEquals(200, second.timestamp());

            assertEquals("25.5", readValue(manager, first));
            assertEquals("30.5", readValue(manager, second));
        }
    }

    @Test
    void recoverShouldKeepLatestRecordForSameKeyWithinSegment()
            throws Exception {

        try (SegmentsManager manager =
                     new SegmentsManager(tempDir, 1024)) {

            manager.append(
                    codec.encode(
                            new Entry(100, "station-1", "25.5")
                    ).data()
            );

            manager.append(
                    codec.encode(
                            new Entry(200, "station-1", "30.5")
                    ).data()
            );

            Map<String, ValueLocation> recovered =
                    new KeyDirRecovery(codec, manager).recover();

            assertEquals(1, recovered.size());

            ValueLocation location = recovered.get("station-1");

            assertEquals(200, location.timestamp());
            assertEquals("30.5", readValue(manager, location));
        }
    }

    @Test
    void recoverShouldRejectIncompleteHeader() throws Exception {
        assertCorruptedRecovery(new byte[]{1, 2, 3, 4, 5});
    }

    @Test
    void recoverShouldRejectIncompleteRecord() throws Exception {
        byte[] record = codec.encode(
                new Entry(100, "key", "value")
        ).data();

        byte[] incomplete = Arrays.copyOf(
                record,
                record.length - 1
        );

        assertCorruptedRecovery(incomplete);
    }

    @Test
    void recoverShouldRejectNegativeHeaderLengths() throws Exception {
        byte[] invalidHeader = ByteBuffer.allocate(codec.headerSize())
                .putLong(100)
                .putInt(-1)
                .putInt(10)
                .array();

        assertCorruptedRecovery(invalidHeader);
    }

    @Test
    void recoverShouldRejectInvalidUtf8() throws Exception {
        byte[] key = "key".getBytes(StandardCharsets.UTF_8);
        byte[] value = {(byte) 0xC3};

        byte[] invalidRecord = ByteBuffer.allocate(
                        codec.headerSize() + key.length + value.length
                )
                .putLong(100)
                .putInt(key.length)
                .putInt(value.length)
                .put(key)
                .put(value)
                .array();

        assertCorruptedRecovery(invalidRecord);
    }

    @Test
    void recoverShouldPreferLatestRecordAcrossSegments() throws Exception {
        byte[] oldRecord = codec.encode(
                new Entry(100, "station-1", "old")
        ).data();

        byte[] newRecord = codec.encode(
                new Entry(200, "station-1", "new")
        ).data();

        Files.write(tempDir.resolve("10.data"), oldRecord);
        Files.write(tempDir.resolve("11.data"), newRecord);

        try (SegmentsManager manager =
                     new SegmentsManager(tempDir, 1024)) {

            assertEquals("11", manager.activeFileId());

            Map<String, ValueLocation> recovered =
                    new KeyDirRecovery(codec, manager).recover();

            ValueLocation location = recovered.get("station-1");

            assertNotNull(location);

            assertEquals(200, location.timestamp());
            assertEquals("new", readValue(manager, location));
        }
    }

    private String readValue(
            SegmentsManager manager,
            ValueLocation location
    ) throws Exception {

        byte[] valueBytes = manager.read(
                location.valuePos(),
                location.fileId(),
                location.valueSize()
        );

        return codec.decodeValue(valueBytes);
    }

    private void assertCorruptedRecovery(byte[] data) throws Exception {
        try (SegmentsManager manager =
                     new SegmentsManager(tempDir, 1024)) {

            manager.append(data);

            KeyDirRecovery recovery =
                    new KeyDirRecovery(codec, manager);

            assertThrows(
                    CorruptRecordException.class,
                    recovery::recover
            );
        }
    }
}