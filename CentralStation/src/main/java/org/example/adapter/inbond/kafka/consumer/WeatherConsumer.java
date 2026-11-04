
package org.example.adapter.inbond.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.example.application.port.in.IngestWeatherEventUseCase;
import org.example.domain.weather.WeatherEvent;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class WeatherConsumer implements AutoCloseable {
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final Properties properties;
    private final String topic;
    private final IngestWeatherEventUseCase ingestionUseCase;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean started = new AtomicBoolean(false);


    private volatile KafkaConsumer<String, WeatherEvent> consumer;

    public WeatherConsumer(
            Properties properties,
            String topic,
            IngestWeatherEventUseCase ingestionUseCase) {

        this.properties = new Properties();
        this.properties.putAll(properties);

        this.topic = topic;
        this.ingestionUseCase = ingestionUseCase;
    }

    public void consume() throws IOException {

        if (!started.compareAndSet(false, true))
            throw new IllegalStateException("Kafka consumer has already been started");

        if (!running.get())
            return;

        KafkaConsumer<String, WeatherEvent> localConsumer =
                new KafkaConsumer<>(properties);

        try (localConsumer) {
            consumer = localConsumer;
            if (!running.get()) {
                return;
            }

            localConsumer.subscribe(Collections.singletonList(topic));

            while (running.get()) {
                ConsumerRecords<String, WeatherEvent> records = localConsumer.poll(POLL_TIMEOUT);

                if (records.isEmpty())
                    continue;

                for (ConsumerRecord<String, WeatherEvent> record : records) {
                    WeatherEvent event = record.value();

                    if (event == null) {
                        throw new IOException(
                                "Received a null WeatherEvent from Kafka. "
                                        + "Partition: " + record.partition()
                                        + ", offset: " + record.offset());
                    }

                    try {
                        ingestionUseCase.ingest(event);
                    } catch (IOException e) {
                        throw new IOException("Failed to persist weather event from Kafka", e);
                    }
                }
                localConsumer.commitSync();
            }

        } catch (WakeupException e) {
            if (running.get())
                throw e;
        } finally {
            consumer = null;
        }
    }
    @Override
    public void close() {
        running.set(false);

        KafkaConsumer<String, WeatherEvent> current = consumer;
        if (current != null) {
            current.wakeup();
        }
    }
}
