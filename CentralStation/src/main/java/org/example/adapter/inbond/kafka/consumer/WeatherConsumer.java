package org.example.adapter.inbond.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.example.application.port.in.IngestWeatherEventUseCase;
import org.example.domain.weather.WeatherEvent;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public class WeatherConsumer implements AutoCloseable {

    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final KafkaConsumer<String, WeatherEvent> consumer;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final IngestWeatherEventUseCase ingestionUseCase;

    public WeatherConsumer(
            Properties properties,
            String topic,
            IngestWeatherEventUseCase ingestionUseCase) {
        this.consumer = new KafkaConsumer<>(properties);
        this.consumer.subscribe(Collections.singletonList(topic));
        this.ingestionUseCase = ingestionUseCase;
    }

    public void consume() {

        try {
            while (running.get()) {

                ConsumerRecords<String, WeatherEvent> records =
                        consumer.poll(POLL_TIMEOUT);

                if (records.isEmpty())
                    continue;

                for (ConsumerRecord<String, WeatherEvent> record : records)
                    ingestionUseCase.ingest(record.value());

                consumer.commitSync();
            }

        } catch (WakeupException e) {
            if (running.get()) {
                throw e;
            }

        } finally {
            consumer.close();
        }
    }

    @Override
    public void close() {
        running.set(false);
        consumer.wakeup();
    }
}