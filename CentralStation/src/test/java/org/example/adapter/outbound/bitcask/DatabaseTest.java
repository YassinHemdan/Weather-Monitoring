package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @TempDir
    Path tempDir;

    @Test
    void setAndFindShouldWork() throws Exception {
        Entry expected = new Entry(100, "station-1", "25.5");

        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            database.set(expected);

            Optional<Entry> result = database.find("station-1");

            assertTrue(result.isPresent());
            assertEquals(expected, result.get());
        }
    }

    @Test
    void findShouldReturnEmptyForMissingKey() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            assertEquals(Optional.empty(), database.find("missing"));
        }
    }

    @Test
    void getShouldThrowWhenKeyDoesNotExist() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            assertThrows(
                    KeyNotFoundException.class,
                    () -> database.get("missing")
            );
        }
    }

    @Test
    void setShouldOverwriteExistingKey() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            database.set(
                    new Entry(100, "station-1", "25.5")
            );

            database.set(
                    new Entry(200, "station-1", "30.5")
            );

            Entry result = database.get("station-1");

            assertEquals(200, result.timestamp());
            assertEquals("station-1", result.key());
            assertEquals("30.5", result.value());
        }
    }

    @Test
    void getAllShouldReturnLatestEntries() throws Exception {
        Entry first = new Entry(100, "station-1", "25.5");
        Entry second = new Entry(200, "station-2", "30.5");
        Entry updatedFirst = new Entry(300, "station-1", "27.5");

        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            database.set(first);
            database.set(second);
            database.set(updatedFirst);

            Map<String, Entry> result = database.getAll();

            assertEquals(2, result.size());
            assertEquals(updatedFirst, result.get("station-1"));
            assertEquals(second, result.get("station-2"));
        }
    }

    @Test
    void databaseShouldRecoverEntriesAfterRestart() throws Exception {
        Entry expected = new Entry(100, "station-1", "25.5");

        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            database.set(expected);
        }

        // Reopen the same directory.
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            assertEquals(expected, database.get("station-1"));
        }
    }

    @Test
    void databaseShouldRecoverEntriesAcrossMultipleSegments() throws Exception {
        Entry first = new Entry(100, "station-1", "25.5");
        Entry second = new Entry(200, "station-2", "30.5");

        try (Database database =
                     new Database(tempDir.toString(), 29)) {

            database.set(first);
            database.set(second);
        }

        try (var files = Files.list(tempDir)) {
            long segmentCount = files
                    .filter(path -> path.toString().endsWith(".data"))
                    .count();

            assertEquals(2, segmentCount);
        }

        try (Database database =
                     new Database(tempDir.toString(), 29)) {

            assertEquals(first, database.get("station-1"));
            assertEquals(second, database.get("station-2"));
        }
    }

    @Test
    void oversizedRecordShouldNotBeAddedToKeyDir() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 17)) {

            assertThrows(
                    IllegalArgumentException.class,
                    () -> database.set(new Entry(100, "k", "v"))
            );

            assertEquals(Optional.empty(), database.find("k"));
        }
    }

    @Test
    void findShouldRejectNullKey() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            assertThrows(
                    NullPointerException.class,
                    () -> database.find(null)
            );
        }
    }
}