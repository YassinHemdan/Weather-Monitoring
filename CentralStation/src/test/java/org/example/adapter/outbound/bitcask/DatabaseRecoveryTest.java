package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseRecoveryTest {

    @TempDir
    Path tempDir;

    @Test
    void recoversAnEntryAfterClosingAndReopeningDatabase() throws Exception {
        String dbPath = tempDir.resolve("db").toString();

        try (Database database = new Database(dbPath)) {
            database.set(new Entry(10L, "station-42", "sunny"));
        }

        try (Database reopened = new Database(dbPath)) {
            Entry result = reopened.get("station-42");

            assertEquals(10L, result.timestamp());
            assertEquals("station-42", result.key());
            assertEquals("sunny", result.value());
        }
    }

    @Test
    void latestValueWinsWhenAKeyWasOverwrittenInTheSameSegment() throws Exception {
        String dbPath = tempDir.resolve("db-same-segment").toString();

        try (Database database = new Database(dbPath, 10_000)) {
            database.set(new Entry(1L, "station-42", "first"));
            database.set(new Entry(2L, "station-42", "second"));
        }

        try (Database reopened = new Database(dbPath, 10_000)) {
            Entry result = reopened.get("station-42");
            assertEquals(2L, result.timestamp());
            assertEquals("second", result.value());
        }
    }

    @Test
    void latestValueWinsWhenAKeyWasOverwrittenAcrossSegments() throws Exception {
        String dbPath = tempDir.resolve("db-multiple-segments").toString();

        // Each record is larger than half the segment capacity, so the third write rotates.
        try (Database database = new Database(dbPath, 64)) {
            database.set(new Entry(1L, "station-42", "one"));
            database.set(new Entry(2L, "station-43", "other"));
            database.set(new Entry(3L, "station-42", "three"));
        }

        try (Database reopened = new Database(dbPath, 64)) {
            assertEquals("three", reopened.get("station-42").value());
            assertEquals("other", reopened.get("station-43").value());
        }
    }

    @Test
    void recoversUtf8KeysAndValues() throws Exception {
        String dbPath = tempDir.resolve("db-utf8").toString();
        String key = "محطة-القاهرة";
        String value = "درجة الحرارة ٢٣°C 🌤️";

        try (Database database = new Database(dbPath)) {
            database.set(new Entry(99L, key, value));
        }

        try (Database reopened = new Database(dbPath)) {
            assertEquals(value, reopened.get(key).value());
        }
    }

    @Test
    void getAllReturnsLatestValueForEveryKeyAfterRecovery() throws Exception {
        String dbPath = tempDir.resolve("db-get-all").toString();

        try (Database database = new Database(dbPath)) {
            database.set(new Entry(1L, "station-42", "old"));
            database.set(new Entry(2L, "station-43", "windy"));
            database.set(new Entry(3L, "station-42", "new"));
        }

        try (Database reopened = new Database(dbPath)) {
            Map<String, Entry> entries = reopened.getAll();

            assertEquals(2, entries.size());
            assertEquals("new", entries.get("station-42").value());
            assertEquals("windy", entries.get("station-43").value());
        }
    }

    @Test
    void emptyDatabaseCanBeClosedAndReopened() throws Exception {
        String dbPath = tempDir.resolve("empty-db").toString();

        try (Database ignored = new Database(dbPath)) {
            // Initialize an empty database.
        }

        try (Database reopened = new Database(dbPath)) {
            assertTrue(reopened.getAll().isEmpty());
            assertEquals(Optional.empty(), reopened.find("missing-key"));
        }
    }
}