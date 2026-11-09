package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BitcaskStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void setAndGetShouldWork() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            BitcaskStore store = new BitcaskStore(database);

            store.set("station-1", "25.5");

            assertEquals(
                    Optional.of("25.5"),
                    store.get("station-1")
            );
        }
    }

    @Test
    void getAllShouldReturnKeyValuePairs() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            BitcaskStore store = new BitcaskStore(database);

            store.set("station-1", "25.5");
            store.set("station-2", "30.5");

            assertEquals(
                    Map.of(
                            "station-1", "25.5",
                            "station-2", "30.5"
                    ),
                    store.getAll()
            );
        }
    }

    @Test
    void getShouldReturnEmptyForMissingKey() throws Exception {
        try (Database database =
                     new Database(tempDir.toString(), 1024)) {

            BitcaskStore store = new BitcaskStore(database);

            assertEquals(
                    Optional.empty(),
                    store.get("missing")
            );
        }
    }
}