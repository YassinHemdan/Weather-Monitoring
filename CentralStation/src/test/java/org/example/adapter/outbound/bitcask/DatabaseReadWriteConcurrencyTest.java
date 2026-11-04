package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseReadWriteConcurrencyTest {

    private static final String KEY = "station-42";
    private static final int WRITER_UPDATES = 2_000;
    private static final int READER_COUNT = 4;
    private static final int MIN_READS_PER_READER = 2_000;
    private static final long TASK_TIMEOUT_SECONDS = 30;

    @TempDir
    Path tempDir;

    @Test
    void readersOnlyObserveCompleteValuesWhileWriterUpdatesSameKey() throws Exception {
        Path databaseDirectory = tempDir.resolve("db");

        try (Database database = new Database(databaseDirectory.toString())) {
            String initialValue = payload(0);
            database.set(new Entry(0L, KEY, initialValue));

            Set<String> allowedValues = java.util.concurrent.ConcurrentHashMap.newKeySet();
            allowedValues.add(initialValue);
            List<String> valuesToWrite = IntStream.rangeClosed(1, WRITER_UPDATES)
                    .mapToObj(DatabaseReadWriteConcurrencyTest::payload)
                    .toList();
            allowedValues.addAll(valuesToWrite);

            CountDownLatch startTogether = new CountDownLatch(1);
            AtomicBoolean writerFinished = new AtomicBoolean(false);
            AtomicInteger totalReads = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(READER_COUNT + 1);
            List<Future<?>> futures = new ArrayList<>();

            try {
                for (int readerNumber = 0; readerNumber < READER_COUNT; readerNumber++) {
                    futures.add(executor.submit(() -> {
                        startTogether.await();

                        int readsPerformed = 0;
                        while (!writerFinished.get()
                                || readsPerformed < MIN_READS_PER_READER) {

                            Entry result = database.get(KEY);

                            assertEquals(KEY, result.key(),
                                    "A read returned the wrong key; the record may have been corrupted.");
                            assertTrue(allowedValues.contains(result.value()),
                                    () -> "A reader observed a value that was never written completely: "
                                            + result.value());

                            readsPerformed++;
                            totalReads.incrementAndGet();
                        }
                        return null;
                    }));
                }
                futures.add(executor.submit(() -> {
                    startTogether.await();
                    try {
                        long timestamp = 1L;
                        for (String value : valuesToWrite) {
                            database.set(new Entry(timestamp++, KEY, value));
                        }
                    } finally {
                        writerFinished.set(true);
                    }
                    return null;
                }));

                startTogether.countDown();

                for (Future<?> future : futures) {
                    future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                assertTrue(totalReads.get() >= READER_COUNT * MIN_READS_PER_READER,
                        "Readers did not perform the expected number of reads.");

                assertEquals(payload(WRITER_UPDATES), database.get(KEY).value());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static String payload(int version) {
        return "version=" + version
                + "|body=" + "x".repeat((version % 127) + 1)
                + "|end=" + version;
    }
}