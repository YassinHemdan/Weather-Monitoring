package org.example.adapter.outbound.bitcask;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseReadWriteConcurrencyTest {

    private static final String KEY = "station-42";

    private static final int WRITER_UPDATES = 2000;
    private static final int READER_COUNT = 4;
    private static final int MIN_READS_PER_READER = 2000;

    private static final int TIMEOUT_SECONDS = 30;

    @TempDir
    Path tempDir;

    @Test
    void readersShouldOnlyObserveCompleteRecords() throws Exception {

        try (Database database =
                     new Database(tempDir.toString(), 4096)) {

            database.set(new Entry(0, KEY, "value-0"));

            ExecutorService executor =
                    Executors.newFixedThreadPool(READER_COUNT + 1);

            CountDownLatch startSignal = new CountDownLatch(1);

            AtomicBoolean writerDone = new AtomicBoolean(false);

            try {
                Future<?> writer = executor.submit(() -> {

                    startSignal.await();

                    try {
                        for (int i = 1; i <= WRITER_UPDATES; i++) {
                            database.set(
                                    new Entry(i, KEY, "value-" + i)
                            );
                        }
                    } finally {
                        writerDone.set(true);
                    }

                    return null;
                });

                List<Future<?>> readers = new ArrayList<>();

                for (int i = 0; i < READER_COUNT; i++) {

                    Future<?> reader = executor.submit(() -> {

                        startSignal.await();

                        int reads = 0;

                        while (!writerDone.get()
                                || reads < MIN_READS_PER_READER) {

                            Entry entry = database.find(KEY)
                                    .orElseThrow();

                            assertEquals(KEY, entry.key());

                            assertTrue(
                                    entry.value().matches("value-\\d+")
                            );

                            long valueVersion = Long.parseLong(
                                    entry.value().substring("value-".length())
                            );

                            assertEquals(
                                    valueVersion,
                                    entry.timestamp(),
                                    "Timestamp and value must belong to the same record"
                            );

                            reads++;
                        }

                        return null;
                    });

                    readers.add(reader);
                }

                startSignal.countDown();

                writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                for (Future<?> reader : readers) {
                    reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                Entry finalEntry = database.get(KEY);

                assertEquals(
                        WRITER_UPDATES,
                        finalEntry.timestamp()
                );

                assertEquals(
                        "value-" + WRITER_UPDATES,
                        finalEntry.value()
                );

            } finally {
                executor.shutdownNow();
            }
        }
    }
}