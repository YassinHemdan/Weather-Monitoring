package org.example.application.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.adapter.outbound.bitcask.Entry;
import org.example.domain.weather.WeatherEvent;

public class WeatherEventEntryMapper {
    private final ObjectMapper objectMapper = new ObjectMapper();
    public Entry toEntry(ConsumerRecord<String, WeatherEvent> record) throws JsonProcessingException {
        String key = String.valueOf(record.value().getStation_id());
        String value = objectMapper.writeValueAsString(record.value());

        return new Entry(
                record.value().getStatus_timestamp(),
                key,
                value
        );
    }
}