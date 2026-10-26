package org.example.bitcask;

import org.example.adapter.outbound.bitcask.Entry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CustomAssertions {
    private CustomAssertions(){}

    public static void assertFileExistsAndNotEmpty(Path path) throws Exception {
        assertTrue(Files.exists(path), "Expected file to exist: " + path);
        assertTrue(Files.size(path) > 0, "Expected non-empty file: " + path);
    }

    public static void assertFileSize(Path path, long expectedSize) throws Exception {
        assertEquals(expectedSize, Files.size(path), "Unexpected file size for: " + path);
    }

    public static void assertEntryEquals(Entry expected, Entry actual) {
        assertEquals(expected.getKey(), actual.getKey(), "Keys do not match");
        assertEquals(expected.getValue(), actual.getValue(), "Values do not match");
        assertEquals(expected.getTimestamp(), actual.getTimestamp(), "Timestamps do not match");
    }
    public static void assertSerializedEntrySize(Path file, Entry entry) throws Exception {
        long expectedSize =
                Long.BYTES +
                        Integer.BYTES * 2 +
                        entry.getKey().getBytes(StandardCharsets.UTF_8).length +
                        entry.getValue().getBytes(StandardCharsets.UTF_8).length;

        assertFileExistsAndNotEmpty(file);
        assertEquals(expectedSize, Files.size(file), "Serialized entry size mismatch");
    }
}
