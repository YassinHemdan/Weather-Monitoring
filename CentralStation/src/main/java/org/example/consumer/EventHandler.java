package org.example.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.model.WeatherEvent;

@FunctionalInterface
public interface EventHandler {
    void handle(ConsumerRecord<String, WeatherEvent> record);
}
