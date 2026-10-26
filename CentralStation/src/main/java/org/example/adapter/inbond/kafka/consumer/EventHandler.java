package org.example.adapter.inbond.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.domain.weather.WeatherEvent;

@FunctionalInterface
public interface EventHandler {
    void handle(ConsumerRecord<String, WeatherEvent> record);
}
