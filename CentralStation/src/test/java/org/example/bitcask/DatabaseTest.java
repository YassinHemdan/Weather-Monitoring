package org.example.bitcask;

import org.example.adapter.outbound.bitcask.Database;
import org.example.adapter.outbound.bitcask.Entry;
import org.example.adapter.outbound.bitcask.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseTest {
    @TempDir
    Path tempDir;

    private Path activeFile() {
        return tempDir.resolve("active.data");
    }

    private Database newDatabase() throws Exception {
        return new Database(tempDir.toString());
    }

    private long serializedSize(Entry entry) {
        return Long.BYTES
                + Integer.BYTES
                + Integer.BYTES
                + entry.getKey().getBytes(StandardCharsets.UTF_8).length
                + entry.getValue().getBytes(StandardCharsets.UTF_8).length;
    }

    private void assertContainsEntry(List<Pair<String, Entry>> entries, String expectedKey, String expectedValue) {
        boolean found = entries.stream().anyMatch(pair ->
                pair.key().equals(expectedKey) &&
                        pair.value().getKey().equals(expectedKey) &&
                        pair.value().getValue().equals(expectedValue)
        );

        assertTrue(found,
                "Expected to find entry with key=" + expectedKey + " and value=" + expectedValue);
    }

    @Nested
    class ConstructorTests {

        @Test
        void createsActiveDataFile() throws Exception {
            try (Database db = newDatabase()) {
                assertTrue(Files.exists(activeFile()));
                assertEquals(0, Files.size(activeFile()));
            }
        }
    }

    @Nested
    class SetTests {

        @Test
        void writesDataToActiveFile() throws Exception {
            try (Database db = newDatabase()) {
                db.set(new Entry("name", "yassin"));
            }

            assertTrue(Files.exists(activeFile()));
            assertTrue(Files.size(activeFile()) > 0);
        }

        @Test
        void writesExpectedNumberOfBytesForSingleEntry() throws Exception {
            Entry entry = new Entry("name", "yassin");

            try (Database db = newDatabase()) {
                db.set(entry);
            }

            CustomAssertions.assertFileExistsAndNotEmpty(activeFile());
            CustomAssertions.assertSerializedEntrySize(activeFile(), entry);
        }

        @Test
        void appendsMultipleEntriesSequentially() throws Exception {
            Entry entry1 = new Entry("k1", "v1");
            Entry entry2 = new Entry("k2", "v2");
            Entry entry3 = new Entry("k3", "v3");

            long expectedSize =
                    serializedSize(entry1) +
                            serializedSize(entry2) +
                            serializedSize(entry3);

            try (Database db = newDatabase()) {
                db.set(entry1);
                db.set(entry2);
                db.set(entry3);
            }

            assertEquals(expectedSize, Files.size(activeFile()));
        }

        @Test
        void duplicateKeyAppendsNewEntryInsteadOfOverwritingOldBytes() throws Exception {
            Entry first = new Entry("key2", "mohamed");
            Entry second = new Entry("key2", "new-value");

            long expectedSize = serializedSize(first) + serializedSize(second);

            try (Database db = newDatabase()) {
                db.set(first);
                db.set(second);
            }

            assertEquals(expectedSize, Files.size(activeFile()));
        }
    }

    @Nested
    class GetTests {

        @Test
        void returnsPreviouslyWrittenEntry() throws Exception {
            try (Database db = newDatabase()) {
                Entry written = new Entry("name", "yassin");
                db.set(written);

                Entry actual = db.get("name");

                assertEquals(written.getKey(), actual.getKey());
                assertEquals(written.getValue(), actual.getValue());
                assertEquals(written.getTimestamp(), actual.getTimestamp());
            }
        }

        @Test
        void returnsLatestEntryForDuplicateKey() throws Exception {
            try (Database db = newDatabase()) {
                Entry first = new Entry("key2", "mohamed");
                Entry second = new Entry("key2", "new-value");

                db.set(first);
                db.set(second);

                Entry actual = db.get("key2");

                assertEquals("key2", actual.getKey());
                assertEquals("new-value", actual.getValue());
                assertEquals(second.getTimestamp(), actual.getTimestamp());
            }
        }

        @Test
        void missingKeyThrowsNullPointerExceptionInCurrentImplementation() throws Exception {
            try (Database db = newDatabase()) {
                assertThrows(NullPointerException.class, () -> db.get("missing"));
            }
        }
    }

    @Nested
    class GetAllTests {

        @Test
        void returnsEmptyListWhenDatabaseHasNoEntries() throws Exception {
            try (Database db = newDatabase()) {
                List<Pair<String, Entry>> all = db.getAll();

                assertNotNull(all);
                assertTrue(all.isEmpty());
            }
        }

        @Test
        void returnsAllUniqueKeysInKeyDir() throws Exception {
            try (Database db = newDatabase()) {
                db.set(new Entry("key1", "yassin"));
                db.set(new Entry("key2", "mohamed"));
                db.set(new Entry("key3", "rana"));

                List<Pair<String, Entry>> all = db.getAll();

                assertEquals(3, all.size());
                assertContainsEntry(all, "key1", "yassin");
                assertContainsEntry(all, "key2", "mohamed");
                assertContainsEntry(all, "key3", "rana");
            }
        }

        @Test
        void returnsLatestEntryWhenAKeyWasWrittenMoreThanOnce() throws Exception {
            try (Database db = newDatabase()) {
                db.set(new Entry("key1", "yassin"));
                db.set(new Entry("key2", "mohamed"));
                db.set(new Entry("key3", "rana"));
                db.set(new Entry("key2", "new-value"));

                List<Pair<String, Entry>> all = db.getAll();

                assertEquals(3, all.size());
                assertContainsEntry(all, "key1", "yassin");
                assertContainsEntry(all, "key2", "new-value");
                assertContainsEntry(all, "key3", "rana");
            }
        }
    }

    @Nested
    class CurrentImplementationLimitationsTests {

        @Test
        void reopeningDatabaseDoesNotRebuildKeyDir() throws Exception {
            try (Database db = newDatabase()) {
                db.set(new Entry("name", "yassin"));
            }

            try (Database db = newDatabase()) {
                assertThrows(NullPointerException.class, () -> db.get("name"));
            }
        }
    }
}