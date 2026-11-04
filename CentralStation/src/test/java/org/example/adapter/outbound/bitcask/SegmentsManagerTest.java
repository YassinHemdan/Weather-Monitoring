package org.example.adapter.outbound.bitcask;

import org.example.adapter.outbound.bitcask.segment.Segment;
import org.example.adapter.outbound.bitcask.segment.SegmentsManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentsManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsAndRegistersAnInitialActiveSegment() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 100)) {
            assertEquals(1, manager.getSegments().size());
            assertEquals(manager.activeFileId(), manager.getSegments().get(0).fileId());
        }
    }

    @Test
    void writesToInitialSegmentAndCanReadTheWrittenBytes() throws Exception {
        byte[] data = "first-record".getBytes(StandardCharsets.UTF_8);

        try (SegmentsManager manager = new SegmentsManager(tempDir, 100)) {
            String fileId = manager.activeFileId();
            long endOffset = manager.write(data);

            assertEquals(data.length, endOffset);
            assertArrayEquals(data, manager.read(0, fileId, data.length));
        }
    }

    @Test
    void rotatesWhenTheNextRecordDoesNotFitAndKeepsOldSegmentReadable() throws Exception {
        byte[] first = "123456".getBytes(StandardCharsets.UTF_8);
        byte[] second = "abcdef".getBytes(StandardCharsets.UTF_8);

        try (SegmentsManager manager = new SegmentsManager(tempDir, 10)) {
            String firstFileId = manager.activeFileId();
            assertEquals(first.length, manager.write(first));

            long secondEndOffset = manager.write(second);
            String secondFileId = manager.activeFileId();

            assertNotEquals(firstFileId, secondFileId,
                    "A new segment should become active when the next record does not fit.");
            assertEquals(second.length, secondEndOffset);
            assertArrayEquals(first, manager.read(0, firstFileId, first.length));
            assertArrayEquals(second, manager.read(0, secondFileId, second.length));
            assertEquals(2, manager.getSegments().size());
        }
    }

    @Test
    void allowsARecordToFillTheSegmentExactly() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 10)) {
            String firstId = manager.activeFileId();

            manager.write(new byte[5]);
            manager.write(new byte[5]);

            assertEquals(firstId, manager.activeFileId());

            manager.write(new byte[]{1});
            assertNotEquals(firstId, manager.activeFileId());
        }
    }

    @Test
    void rejectsARecordLargerThanTheMaximumSegmentSize() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 10)) {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.write(new byte[11]));
        }
    }

    @Test
    void reloadsSegmentsInAscendingNumericIdOrder() throws Exception {
        String firstId;
        String secondId;

        try (SegmentsManager manager = new SegmentsManager(tempDir, 10)) {
            firstId = manager.activeFileId();
            manager.write(new byte[6]);
            manager.write(new byte[6]); // Forces a rotation.
            secondId = manager.activeFileId();
        }

        try (SegmentsManager reopened = new SegmentsManager(tempDir, 10)) {
            List<Segment> segments = reopened.getSegments();

            assertEquals(2, segments.size());
            assertEquals(firstId, segments.get(0).fileId());
            assertEquals(secondId, segments.get(1).fileId());
            assertEquals(secondId, reopened.activeFileId());
        }
    }
}