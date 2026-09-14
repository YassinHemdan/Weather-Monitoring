package org.example.consumer;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.example.model.WeatherEvent;

import java.util.List;
import java.util.Properties;

public class WeatherConsumer implements AutoCloseable{
    private final KafkaConsumer<String, WeatherEvent> consumer;

    public WeatherConsumer(Properties properties, String topic) {
        this.consumer = new KafkaConsumer<>(properties);
        this.consumer.subscribe(List.of(topic));
    }

    public void start(RecordHandler handler) {
        // TODO: listen to events and pass it to the given handler
    }

    @Override
    public void close() throws Exception {
        this.consumer.close();
    }
}
