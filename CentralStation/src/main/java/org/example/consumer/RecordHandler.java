package org.example.consumer;


import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.model.WeatherEvent;

import java.io.IOException;

@FunctionalInterface
public interface RecordHandler {
    void handle(ConsumerRecord<String, WeatherEvent> record) throws IOException;
}
