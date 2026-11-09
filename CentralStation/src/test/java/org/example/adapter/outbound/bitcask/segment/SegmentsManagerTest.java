package org.example.adapter.outbound.bitcask.segment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentsManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void appendShouldRotateWhenActiveSegmentCannotFitRecord() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 4)) {

            AppendResult first = manager.append(
                    new byte[]{1, 2, 3, 4}
            );

            AppendResult second = manager.append(
                    new byte[]{5, 6}
            );

            assertEquals(0, first.startOffset());
            assertEquals(4, first.length());

            assertNotEquals(first.segmentId(), second.segmentId());

            assertEquals(0, second.startOffset());
            assertEquals(2, second.length());

            List<SegmentInfo> segments = manager.getSegments();

            assertEquals(2, segments.size());

            assertEquals(
                    SegmentState.SEALED,
                    getSegment(segments, first.segmentId()).state()
            );

            assertEquals(
                    SegmentState.ACTIVE,
                    getSegment(segments, second.segmentId()).state()
            );
        }
    }

    @Test
    void shouldAcceptRecordThatExactlyFillsSegment() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 4)) {

            AppendResult result = manager.append(
                    new byte[]{1, 2, 3, 4}
            );

            assertEquals(4, result.length());
            assertEquals(result.segmentId(), manager.activeFileId());
        }
    }

    @Test
    void shouldRejectRecordLargerThanMaximumSegmentSize() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 4)) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> manager.append(new byte[5])
            );

            assertEquals(1, manager.getSegments().size());
        }
    }

    @Test
    void shouldRejectNonPositiveMaximumSegmentSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentsManager(tempDir, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentsManager(tempDir, -1)
        );
    }

    @Test
    void shouldLoadExistingSegmentsAfterRestart() throws Exception {
        String previousId;
        String activeId;

        try (SegmentsManager manager = new SegmentsManager(tempDir, 4)) {

            AppendResult first = manager.append(
                    new byte[]{1, 2, 3, 4}
            );

            AppendResult second = manager.append(
                    new byte[]{5, 6}
            );

            previousId = first.segmentId();
            activeId = second.segmentId();
        }

        // Simulate restarting the database.
        try (SegmentsManager manager = new SegmentsManager(tempDir, 4)) {

            assertEquals(activeId, manager.activeFileId());

            List<SegmentInfo> segments = manager.getSegments();

            assertEquals(2, segments.size());

            assertEquals(
                    SegmentState.SEALED,
                    getSegment(segments, previousId).state()
            );

            assertEquals(
                    SegmentState.ACTIVE,
                    getSegment(segments, activeId).state()
            );

            assertArrayEquals(
                    new byte[]{1, 2, 3, 4},
                    manager.read(0, previousId, 4)
            );

            assertArrayEquals(
                    new byte[]{5, 6},
                    manager.read(0, activeId, 2)
            );
        }
    }

    @Test
    void readShouldThrowWhenSegmentDoesNotExist() throws Exception {
        try (SegmentsManager manager = new SegmentsManager(tempDir, 100)) {

            assertThrows(
                    SegmentNotFoundException.class,
                    () -> manager.read(0, "999999", 10)
            );
        }
    }

    private SegmentInfo getSegment(
            List<SegmentInfo> segments,
            String id
    ) {
        return segments.stream()
                .filter(segment -> segment.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}